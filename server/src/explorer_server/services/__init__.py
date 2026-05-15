"""Service layer — 비즈니스 로직.

Phase 1 신규 모듈 (예정):
- run_ingest.py     : 단말에서 회수한 events.jsonl 파싱 → storage 저장
- state_graph.py    : StateGraph 직렬화/역직렬화
- coverage_calc.py  : Tier 별 hit 기여도 집계

규칙: router 는 service 만 호출, service 는 storage 만 호출.
"""
