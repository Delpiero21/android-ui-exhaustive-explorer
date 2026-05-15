"""VLM (Vision Language Model) proxy.

Phase 2 신규 모듈 (예정):
- ollama_proxy.py   : 사내 Ollama (Qwen2-VL / MiniCPM-V) 호출 wrapper
- prompt.py         : state graph 요약을 prompt 로 직렬화
- cache.py          : fp 단위 결과 캐싱

보안 경계:
- 환경 변수 EXPLORER_OLLAMA_HOSTS 의 화이트리스트만 허용
- EXPLORER_OFFLINE=1 이면 일체 호출 안 함 (외부 API 차단 모드)
"""
