# sherlock

보드게임 동아리 '셜록'의 운영 시스템입니다.

## 요구 사항
- Java 21
- Node.js 22 이상

## 로컬 실행

### Backend
```bash
cd backend
./gradlew bootRun      # http://localhost:8080 (기본 H2 사용)
./gradlew test         # 테스트
```
헬스 체크: `curl http://localhost:8080/api/health` → `{"status":"UP"}`

운영(`prod`) 프로필은 PostgreSQL을 사용하며 접속 정보를 환경변수로 받습니다.
```bash
export DB_URL=jdbc:postgresql://<host>:5432/<db>
export DB_USERNAME=<user>
export DB_PASSWORD=<password>
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

### Frontend
```bash
cd frontend
npm ci
npm run dev            # http://localhost:5173
npm test -- --run      # 테스트
npm run build          # 빌드
```
