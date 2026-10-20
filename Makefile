# Windows 本地启动；连接配置见 .env.example 和 scripts/README.md。
.DEFAULT_GOAL := help
POWERSHELL := powershell.exe
RUN := $(POWERSHELL) -NoProfile -ExecutionPolicy Bypass -File scripts/run-project.ps1

.PHONY: help check install build dev frontend backend infra infra-status

help:
	@$(RUN) -Help

check:
	@$(RUN) -Action check

install:
	@$(RUN) -Action install

build:
	@$(RUN) -Action build

# 先检查配置，再并行启动；递归 make 将 Ctrl+C 传递给前台子进程。
dev: check
	+@"$(MAKE)" --no-print-directory -j2 backend frontend

infra:
	@$(RUN) -Action infra

infra-status:
	@$(RUN) -Action infra-status

frontend:
	@$(RUN) -Action frontend

backend:
	@$(RUN) -Action backend
