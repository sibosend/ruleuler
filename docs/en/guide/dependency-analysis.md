# Dependency Analysis

## Overview

As rule projects evolve, variable libraries, rule sets, decision tables, decision trees, and decision flows form a complex dependency graph. Modifying a shared variable or foundational rule without visibility into downstream references can cause unexpected breakage.

The **Dependency Analysis** module provides visual upstream/downstream lineage tracking and automatically derives the list of affected top-level **Knowledge Packages**.

---

## Core Capabilities

### 1. Automatic Dependency Graph Construction

Dependencies are extracted dynamically across all project files, establishing links across:
- Knowledge Packages → Components (Decision Flows / Rules / Variable Libraries)
- Decision Flows → Child Rules / Variable Libraries
- Rule Components (Decision Tables, Rule Sets, etc.) → Variables / Parameters / Action Libraries

### 2. Bidirectional Lineage Tracking

- **Upstream Dependencies (What it depends on)**: Lists external rules, variable libraries, parameter libraries, and action libraries directly or indirectly referenced by the target file.
- **Downstream References (What depends on it)**: Lists parent files (rule sets, decision flows, knowledge packages) that reference the target file, showing what will be affected if the current file is modified.

### 3. Impact Assessment (Affected Knowledge Packages)

Starting from downstream references, the analyzer traverses upward to the root deployment units and lists all **Knowledge Packages** impacted by changes to the selected resource, helping scope regression testing before release.

---

## Entry Points

Open the Dependency Analysis drawer (`DependencyDrawer`) from two places in the Admin Console:

1. **Resource Tree Context Menu**: Right-click any resource file in the left project tree and select **Dependency Analysis**.
2. **Editor Toolbar**: Click the **Dependency Analysis** button in the top toolbar while editing a decision component.

---

## Best Practices

- **Troubleshoot Missing References**: Inspect upstream dependencies when a package build fails due to renamed or deleted variables.
- **Pre-Change Impact Review**: Check downstream references and affected knowledge packages before modifying shared variables or base rules.
- **Clean Up Orphaned Resources**: Rule files or variable categories with zero downstream references are unused and can be safely archived or removed.

