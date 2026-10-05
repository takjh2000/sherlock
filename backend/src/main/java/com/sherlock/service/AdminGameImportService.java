package com.sherlock.service;

import com.sherlock.domain.Game;
import com.sherlock.domain.GameCategory;
import com.sherlock.domain.GameNote;
import com.sherlock.domain.Member;
import com.sherlock.domain.RentalStatus;
import com.sherlock.dto.GameImportResponse;
import com.sherlock.repository.GameNoteRepository;
import com.sherlock.repository.GameRepository;
import com.sherlock.repository.MemberRepository;
import com.sherlock.repository.RentalRepository;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기존 엑셀 게임 목록 가져오기.
 * 시트 '보드게임_현황'은 BOARD_GAME, '크라임씬_현황'은 CRIME_SCENE으로 읽고 다른 시트는 무시한다.
 */
@Service
@Transactional
public class AdminGameImportService {

	/** 한 번에 처리할 수 있는 최대 데이터 행 수(게임 이름이 있는 행 기준). */
	static final int MAX_IMPORT_ROWS = 5000;
	private static final int MAX_NAME_LENGTH = 255;
	private static final int MAX_OWNER_LENGTH = 255;
	private static final int MAX_NOTE_LENGTH = 1000;
	private static final String COL_NAME = "게임 이름";
	private static final String COL_OWNER = "소유자";
	private static final String COL_QUANTITY = "전체 수량";
	private static final String COL_NOTE = "비고";
	private static final String SHEET_BOARD_GAME = "보드게임_현황";
	private static final String SHEET_CRIME_SCENE = "크라임씬_현황";

	private final GameRepository gameRepository;
	private final GameNoteRepository gameNoteRepository;
	private final MemberRepository memberRepository;
	private final RentalRepository rentalRepository;

	public AdminGameImportService(GameRepository gameRepository, GameNoteRepository gameNoteRepository,
			MemberRepository memberRepository, RentalRepository rentalRepository) {
		this.gameRepository = gameRepository;
		this.gameNoteRepository = gameNoteRepository;
		this.memberRepository = memberRepository;
		this.rentalRepository = rentalRepository;
	}

	/**
	 * 같은 분류 + 같은 이름의 게임이 있으면 수량/소유자를 갱신하고, 없으면 새로 만든다.
	 * 비고는 특이사항으로 추가하되 같은 내용이 이미 있으면 추가하지 않는다.
	 * 잘못된 행은 반영하지 않고 사유와 함께 돌려준다.
	 */
	public GameImportResponse importFromExcel(String adminStudentId, InputStream in) {
		Member author = memberRepository.findByStudentId(adminStudentId)
				.orElseThrow(() -> new AdminException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "로그인이 필요합니다."));

		List<ParsedRow> rows = readRows(in);

		Map<String, Game> existing = new HashMap<>();
		for (Game game : gameRepository.findByDeletedFalse()) {
			existing.put(key(game.getCategory(), game.getName()), game);
		}

		int created = 0;
		int updated = 0;
		List<GameImportResponse.Failure> failed = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (ParsedRow row : rows) {
			String problem = row.problem() != null ? row.problem() : validate(row);
			String key = key(row.category(), row.name());
			if (problem == null && !seen.add(key)) {
				problem = "파일 안에서 같은 분류의 같은 이름이 중복됩니다.";
			}
			Game game = existing.get(key);
			boolean isNew = false;
			if (problem == null && game != null) {
				problem = update(game, row);
			} else if (problem == null) {
				game = gameRepository.save(new Game(row.name(), row.category(), row.owner(), row.quantity()));
				existing.put(key, game);
				isNew = true;
			}
			if (problem != null) {
				failed.add(new GameImportResponse.Failure(row.sheet(), row.rowNumber(), row.name(), problem));
				continue;
			}
			if (isNew) {
				created++;
			} else {
				updated++;
			}
			addNote(game, row.note(), author);
		}
		return new GameImportResponse(created, updated, failed);
	}

	/** 대여와 같은 행 락을 잡고 갱신한다. 대여 중인 수량보다 적게 줄이면 사유를 돌려준다. */
	private String update(Game game, ParsedRow row) {
		Game locked = gameRepository.findActiveByIdForUpdate(game.getId()).orElse(null);
		if (locked == null) {
			return "게임을 찾을 수 없습니다.";
		}
		long active = rentalRepository.countByGameAndStatus(locked, RentalStatus.RENTED);
		if (row.quantity() < active) {
			return "대여 중인 수량(" + active + "개)보다 적게 줄일 수 없습니다.";
		}
		locked.update(locked.getName(), locked.getCategory(), row.owner(), row.quantity());
		return null;
	}

	private void addNote(Game game, String note, Member author) {
		if (note == null) {
			return;
		}
		boolean duplicated = gameNoteRepository.findByGameOrderByCreatedAtDesc(game).stream()
				.anyMatch(n -> n.getContent().trim().equals(note));
		if (!duplicated) {
			gameNoteRepository.save(new GameNote(game, note, author));
		}
	}

	private static String key(GameCategory category, String name) {
		return category + "\n" + name;
	}

	private static String validate(ParsedRow row) {
		if (row.name().length() > MAX_NAME_LENGTH) {
			return "게임 이름이 너무 깁니다.";
		}
		if (row.owner() != null && row.owner().length() > MAX_OWNER_LENGTH) {
			return "소유자가 너무 깁니다.";
		}
		if (row.note() != null && row.note().length() > MAX_NOTE_LENGTH) {
			return "비고가 너무 깁니다.";
		}
		return null;
	}

	/** problem이 있으면 읽는 단계에서 이미 실패한 행이다. */
	private record ParsedRow(String sheet, GameCategory category, int rowNumber, String name, String owner,
			int quantity, String note, String problem) {
	}

	private static List<ParsedRow> readRows(InputStream in) {
		List<ParsedRow> rows = new ArrayList<>();
		DataFormatter formatter = new DataFormatter();
		try (XSSFWorkbook workbook = new XSSFWorkbook(in)) {
			FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
			boolean found = false;
			// 시트 순서와 무관하게 결과가 일정하도록 보드게임 → 크라임씬 순서로 읽는다.
			for (String sheetName : List.of(SHEET_BOARD_GAME, SHEET_CRIME_SCENE)) {
				Sheet sheet = workbook.getSheet(sheetName);
				if (sheet == null) {
					continue;
				}
				found = true;
				GameCategory category = sheetName.equals(SHEET_BOARD_GAME) ? GameCategory.BOARD_GAME
						: GameCategory.CRIME_SCENE;
				readSheet(sheet, category, formatter, evaluator, rows);
			}
			if (!found) {
				throw new AdminException(HttpStatus.BAD_REQUEST, "SHEET_NOT_FOUND",
						"'" + SHEET_BOARD_GAME + "' 또는 '" + SHEET_CRIME_SCENE + "' 시트가 없습니다.");
			}
		} catch (IOException | RuntimeException e) {
			if (e instanceof AdminException admin) {
				throw admin;
			}
			throw invalidFile();
		}
		return rows;
	}

	private static void readSheet(Sheet sheet, GameCategory category, DataFormatter formatter,
			FormulaEvaluator evaluator, List<ParsedRow> rows) {
		Map<String, Integer> columns = new HashMap<>();
		int headerIndex = -1;
		for (int i = sheet.getFirstRowNum(); i <= sheet.getLastRowNum(); i++) {
			Row row = sheet.getRow(i);
			if (row == null) {
				continue;
			}
			for (Cell cell : row) {
				String header = text(formatter, evaluator, cell);
				if (!header.isEmpty()) {
					columns.put(header, cell.getColumnIndex());
				}
			}
			if (!columns.isEmpty()) {
				headerIndex = i;
				break;
			}
		}
		if (!columns.containsKey(COL_NAME) || !columns.containsKey(COL_QUANTITY)) {
			throw new AdminException(HttpStatus.BAD_REQUEST, "INVALID_HEADER",
					"'" + sheet.getSheetName() + "' 시트에 '" + COL_NAME + "', '" + COL_QUANTITY + "' 열이 필요합니다.");
		}
		int nameCol = columns.get(COL_NAME);
		int quantityCol = columns.get(COL_QUANTITY);
		int ownerCol = columns.getOrDefault(COL_OWNER, -1);
		int noteCol = columns.getOrDefault(COL_NOTE, -1);

		for (int i = headerIndex + 1; i <= sheet.getLastRowNum(); i++) {
			Row row = sheet.getRow(i);
			if (row == null) {
				continue;
			}
			// 시트가 2000행까지 잡혀 있어도 게임 이름이 빈 행은 건너뛴다.
			String name = text(formatter, evaluator, row.getCell(nameCol));
			if (name.isEmpty()) {
				continue;
			}
			if (rows.size() >= MAX_IMPORT_ROWS) {
				throw new AdminException(HttpStatus.BAD_REQUEST, "TOO_MANY_ROWS",
						"한 번에 " + MAX_IMPORT_ROWS + "행까지만 가져올 수 있습니다.");
			}
			String owner = ownerCol < 0 ? "" : text(formatter, evaluator, row.getCell(ownerCol));
			String note = noteCol < 0 ? "" : text(formatter, evaluator, row.getCell(noteCol));
			Integer quantity = parseQuantity(text(formatter, evaluator, row.getCell(quantityCol)));
			String problem = quantity == null ? "전체 수량은 1 이상의 정수여야 합니다." : null;
			rows.add(new ParsedRow(sheet.getSheetName(), category, i + 1, name, owner.isEmpty() ? null : owner,
					quantity == null ? 0 : quantity, note.isEmpty() ? null : note, problem));
		}
	}

	/** 1.0처럼 실수로 저장된 값도 정수로 읽는다. 정수가 아니거나 1 미만이면 null. */
	private static Integer parseQuantity(String text) {
		try {
			BigDecimal value = new BigDecimal(text.replace(",", ""));
			if (value.stripTrailingZeros().scale() > 0 || value.signum() <= 0
					|| value.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
				return null;
			}
			return value.intValueExact();
		} catch (NumberFormatException | ArithmeticException e) {
			return null;
		}
	}

	/** 숫자로 저장된 게임 이름(예: 719)도 문자열로 읽는다. */
	private static String text(DataFormatter formatter, FormulaEvaluator evaluator, Cell cell) {
		return cell == null ? "" : formatter.formatCellValue(cell, evaluator).trim();
	}

	private static AdminException invalidFile() {
		return new AdminException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "올바른 xlsx 파일이 아닙니다.");
	}
}
