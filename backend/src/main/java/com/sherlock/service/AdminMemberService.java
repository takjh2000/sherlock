package com.sherlock.service;

import com.sherlock.domain.Member;
import com.sherlock.domain.Role;
import com.sherlock.dto.AdminMemberResponse;
import com.sherlock.dto.MemberImportResponse;
import com.sherlock.dto.PageResponse;
import com.sherlock.repository.MemberRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 관리자의 회원 명단 관리. */
@Service
@Transactional
public class AdminMemberService {

	static final int MAX_PAGE_SIZE = 100;
	/** 엑셀 한 번에 등록할 수 있는 최대 데이터 행 수. */
	static final int MAX_IMPORT_ROWS = 2000;
	private static final int MAX_STUDENT_ID_LENGTH = 20;
	private static final int MAX_NAME_LENGTH = 50;
	private static final String HEADER_STUDENT_ID = "학번";

	private final MemberRepository memberRepository;

	public AdminMemberService(MemberRepository memberRepository) {
		this.memberRepository = memberRepository;
	}

	@Transactional(readOnly = true)
	public PageResponse<AdminMemberResponse> list(String keyword, int page, int size) {
		PageRequest pageable = PageRequest.of(Math.max(0, page),
				Math.min(Math.max(1, size), MAX_PAGE_SIZE), Sort.by("studentId"));
		Page<Member> members = keyword == null || keyword.isBlank()
				? memberRepository.findAll(pageable)
				: memberRepository.findByStudentIdContainingIgnoreCaseOrNameContainingIgnoreCase(
						keyword.trim(), keyword.trim(), pageable);
		return PageResponse.from(members.map(AdminMemberResponse::from));
	}

	public AdminMemberResponse create(String studentId, String name) {
		String id = studentId.trim();
		if (memberRepository.findByStudentId(id).isPresent()) {
			throw duplicate();
		}
		try {
			return AdminMemberResponse
					.from(memberRepository.saveAndFlush(new Member(id, name.trim(), Role.MEMBER)));
		} catch (DataIntegrityViolationException e) {
			// 동시에 같은 학번이 등록된 경우
			throw duplicate();
		}
	}

	/**
	 * xlsx 첫 시트의 A열(학번), B열(이름)을 읽어 등록한다. 첫 행이 "학번" 머리글이면 건너뛴다.
	 * 이미 있거나 파일 안에서 중복되거나 값이 올바르지 않은 행은 등록하지 않고 사유와 함께 돌려준다.
	 */
	public MemberImportResponse importFromExcel(InputStream in) {
		List<ParsedRow> rows = readRows(in);

		Set<String> existing = new HashSet<>();
		memberRepository.findByStudentIdIn(rows.stream().map(ParsedRow::studentId).toList())
				.forEach(m -> existing.add(m.getStudentId()));

		List<MemberImportResponse.Skipped> skipped = new ArrayList<>();
		List<Member> toSave = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (ParsedRow row : rows) {
			String problem = validate(row);
			if (problem == null && !seen.add(row.studentId())) {
				problem = "파일 안에서 학번이 중복됩니다.";
			}
			if (problem == null && existing.contains(row.studentId())) {
				problem = "이미 등록된 학번입니다.";
			}
			if (problem != null) {
				skipped.add(new MemberImportResponse.Skipped(row.rowNumber(), row.studentId(), problem));
			} else {
				toSave.add(new Member(row.studentId(), row.name(), Role.MEMBER));
			}
		}
		memberRepository.saveAll(toSave);
		return new MemberImportResponse(toSave.size(), skipped);
	}

	/** 비활성화한다. 로그인이 막히고 기존 세션도 다음 요청에서 끊긴다. 본인은 비활성화할 수 없다. */
	public AdminMemberResponse deactivate(String adminStudentId, Long id) {
		Member member = find(id);
		rejectSelf(adminStudentId, member);
		member.deactivate();
		return AdminMemberResponse.from(member);
	}

	/** 비밀번호를 지워 다시 첫 로그인 상태로 만든다. 기존 세션은 다음 요청에서 무효화된다. */
	public AdminMemberResponse resetPassword(String adminStudentId, Long id) {
		Member member = find(id);
		rejectSelf(adminStudentId, member);
		member.resetPassword();
		return AdminMemberResponse.from(member);
	}

	private Member find(Long id) {
		return memberRepository.findById(id)
				.orElseThrow(() -> new AdminException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND",
						"회원을 찾을 수 없습니다."));
	}

	private static void rejectSelf(String adminStudentId, Member member) {
		if (member.getStudentId().equals(adminStudentId)) {
			throw new AdminException(HttpStatus.BAD_REQUEST, "CANNOT_MODIFY_SELF",
					"본인 계정에는 사용할 수 없습니다.");
		}
	}

	private static AdminException duplicate() {
		return new AdminException(HttpStatus.CONFLICT, "DUPLICATE_STUDENT_ID", "이미 등록된 학번입니다.");
	}

	private static String validate(ParsedRow row) {
		if (row.studentId().isEmpty() || row.name().isEmpty()) {
			return "학번과 이름이 모두 필요합니다.";
		}
		if (row.studentId().length() > MAX_STUDENT_ID_LENGTH || row.studentId().chars().anyMatch(Character::isWhitespace)) {
			return "학번 형식이 올바르지 않습니다.";
		}
		if (row.name().length() > MAX_NAME_LENGTH) {
			return "이름이 너무 깁니다.";
		}
		return null;
	}

	private record ParsedRow(int rowNumber, String studentId, String name) {
	}

	private static List<ParsedRow> readRows(InputStream in) {
		List<ParsedRow> rows = new ArrayList<>();
		DataFormatter formatter = new DataFormatter();
		try (XSSFWorkbook workbook = new XSSFWorkbook(in)) {
			if (workbook.getNumberOfSheets() == 0) {
				throw invalidFile();
			}
			Sheet sheet = workbook.getSheetAt(0);
			for (int i = sheet.getFirstRowNum(); i <= sheet.getLastRowNum(); i++) {
				Row row = sheet.getRow(i);
				if (row == null) {
					continue;
				}
				String studentId = text(formatter, row.getCell(0));
				String name = text(formatter, row.getCell(1));
				if (studentId.isEmpty() && name.isEmpty()) {
					continue;
				}
				if (rows.isEmpty() && i == sheet.getFirstRowNum() && studentId.equals(HEADER_STUDENT_ID)) {
					continue;
				}
				if (rows.size() >= MAX_IMPORT_ROWS) {
					throw new AdminException(HttpStatus.BAD_REQUEST, "TOO_MANY_ROWS",
							"한 번에 " + MAX_IMPORT_ROWS + "명까지만 등록할 수 있습니다.");
				}
				rows.add(new ParsedRow(i + 1, studentId, name));
			}
		} catch (IOException | RuntimeException e) {
			if (e instanceof AdminException admin) {
				throw admin;
			}
			throw invalidFile();
		}
		return rows;
	}

	private static String text(DataFormatter formatter, Cell cell) {
		return cell == null ? "" : formatter.formatCellValue(cell).trim();
	}

	private static AdminException invalidFile() {
		return new AdminException(HttpStatus.BAD_REQUEST, "INVALID_FILE", "올바른 xlsx 파일이 아닙니다.");
	}
}
