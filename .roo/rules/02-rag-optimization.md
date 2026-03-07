# RAG & Context Management

Optimize how you search and retrieve information from the workspace:

1. **Search Hierarchy**:
    - First, use `list_files` to understand the directory structure.
    - Second, use `search_files` (grep) for specific keywords.
    - Only then use `read_file` for the most relevant matches.
2. **Context Window Preservation**: Do not read large files (over 500 lines) entirely. Use `read_file` with line range parameters if you only need a specific function or class.
3. **Ignore Patterns**: Always ignore `node_modules`, `dist`, `build`, and any minified files during search, even if not explicitly listed in .gitignore.
4. **Relevance Filtering**: When RAG returns multiple snippets, evaluate their relevance before processing. If snippets are near-identical, process only the most recent or most complete version.