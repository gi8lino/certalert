.DEFAULT_GOAL := help

LOCALBIN ?= bin

$(LOCALBIN):
	@mkdir -p "$@"

## Tool Versions
# renovate: datasource=github-releases depName=gi8lino/dev-tools
DEV_TOOLS_VERSION ?= v0.5.0

## Tool Binaries
DEV_TOOL_NAMES := dev-port open-browser dev-tag make-help go-install-tool
DEV_TOOL_TARGETS := $(addprefix $(LOCALBIN)/,$(DEV_TOOL_NAMES))
DEV_TOOL_VERSIONED := $(addsuffix -$(DEV_TOOLS_VERSION),$(DEV_TOOL_TARGETS))

DEV_PORT := $(LOCALBIN)/dev-port
OPEN_BROWSER := $(LOCALBIN)/open-browser
DEV_TAG := $(LOCALBIN)/dev-tag
MAKE_HELP := $(LOCALBIN)/make-help
GO_INSTALL_TOOL := $(LOCALBIN)/go-install-tool

# Run a local tool while displaying only its executable name.
define run-tool
@printf '%s\n' '$(notdir $(1)) $(2)'
@$(1) $(2)
endef
##@ Tagging

VERSION_PREFIX ?= v

.PHONY: current
current: $(DEV_TAG) ## Show the current semantic version tag.
	$(call run-tool,$(DEV_TAG),--prefix "$(VERSION_PREFIX)" current)

.PHONY: patch
patch: $(DEV_TAG) ## Create a new patch release (x.y.Z+1).
	$(call run-tool,$(DEV_TAG),--prefix "$(VERSION_PREFIX)" patch)

.PHONY: minor
minor: $(DEV_TAG) ## Create a new minor release (x.Y+1.0).
	$(call run-tool,$(DEV_TAG),--prefix "$(VERSION_PREFIX)" minor)

.PHONY: major
major: $(DEV_TAG) ## Create a new major release (X+1.0.0).
	$(call run-tool,$(DEV_TAG),--prefix "$(VERSION_PREFIX)" major)

.PHONY: tag
tag: current

.PHONY: push
push: ## Push tags to the configured remote.
	git push --tags

##@ General

.PHONY: help
help: $(MAKE_HELP) ## Display this help.
	@$(MAKE_HELP) $(MAKEFILE_LIST)


##@ Development tools

.PHONY: dev-tools
dev-tools: $(DEV_TOOL_TARGETS) ## Download the pinned development tools.

$(DEV_TOOL_TARGETS): $(LOCALBIN)/%: $(LOCALBIN)/%-$(DEV_TOOLS_VERSION)
	@ln -sf "$(notdir $<)" "$@"

$(DEV_TOOL_VERSIONED): $(LOCALBIN)/%-$(DEV_TOOLS_VERSION): | $(LOCALBIN)
	$(call download-dev-tool,$*,$@)

# download-dev-tool downloads a versioned tool from gi8lino/dev-tools.
# $1 - release asset name
# $2 - versioned destination path
define download-dev-tool
	@set -eu; \
	tmp="$(2).tmp"; \
	trap 'rm -f "$$tmp"' EXIT INT TERM; \
	echo "Downloading gi8lino/dev-tools $(DEV_TOOLS_VERSION) $(1)"; \
	curl --fail --silent --show-error --location \
		"https://github.com/gi8lino/dev-tools/releases/download/$(DEV_TOOLS_VERSION)/$(1)" \
		-o "$$tmp"; \
	chmod +x "$$tmp"; \
	mv "$$tmp" "$(2)"; \
	trap - EXIT INT TERM
endef
