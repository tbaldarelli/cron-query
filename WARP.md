# Project Rules for cron-query

## Environment
- This project runs on Linux systems (migrated from Global rule)

## Development Preferences  
- Use Python for main product development
- Keep Go and Rust as options for future learning phases

## Additional Project-Specific Rules

### Git Commit Messages on Windows
When running git commit commands on Windows PowerShell:
- Use double quotes and let PowerShell naturally continue the multi-line string
- After opening quote, press Enter - PowerShell will show `>>` prompt for continuation
- Type each line naturally, pressing Enter between lines
- Close with final quote
- Example format:
  ```
  git commit -m "title
  
  - bullet point 1
  - bullet point 2"
  ```
- This creates literal newlines, not escape sequences
- Do NOT use `\n` escape sequences or here-strings
