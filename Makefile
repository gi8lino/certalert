.DEFAULT_GOAL := help

## Tool Versions
# renovate: datasource=github-releases depName=gi8lino/dev-tools
DEV_TOOLS_VERSION ?= v0.9.0

## Shared development tools

include bin/dev-tools.mk
include $(call dev-tools-module,tag)
include $(call dev-tools-module,help)

##@ Development tools

.PHONY: dev-tools
dev-tools: $(DEV_TAG) $(MAKE_HELP) ## Download the pinned development tools.
