"""Storage layer — 파일시스템 기반 저장소.

Phase 1 신규 모듈 (예정):
- file_store.py     : data/{run_id}/{events.jsonl, screenshots/...} 관리
- index.py          : run_id 인덱싱 + 빠른 조회

위치: 서버 프로세스가 띄워진 디렉토리 하위의 data/ (이미 .gitignore 로 commit 차단).
"""
