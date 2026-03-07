# Anti-Looping & Process Control

To prevent infinite loops, redundant analysis, and "reasoning spirals," follow these rules:

1. **Strict File Access**: Do not read the same file more than twice. If the required information was already retrieved, you MUST extract it from your previous conversation context.
2. **Linear Progress Requirement**: Every tool call must produce a NEW piece of information or a NEW state change. If a tool output doesn't move the task forward, do not retry the same approach; switch to a different strategy immediately.
3. **5-Step Analysis Cap**: If you perform 5 consecutive "read" or "search" actions without modifying a file or executing code, you must:
   - Summarize what you have learned so far.
   - State why a solution hasn't been reached.
   - Ask the user for a specific direction or clarification.
4. **Duplicate Action Prevention**: Before any `read_file`, `list_files`, or `search_files`, check the last 10 messages. If the exact same action (with same parameters) was performed, you are FORBIDDEN from repeating it. Use the existing data.
5. **Loop-Break Protocol**: If you notice yourself re-stating the same reasoning or getting stuck in a "thinking loop":
   - **Stop immediately.**
   - Explicitly output: "LOOP DETECTED: [Briefly state the repeating logic]."
   - Propose an entirely different technical path or ask for a "hard reset" of the strategy.
6. **No "Thinking in Circles"**: Avoid re-analyzing the same problem statement. If the analysis is done, transition immediately to the `write_file` or `execute_command` phase. Do not provide "summary thoughts" that repeat previous findings unless explicitly asked.
