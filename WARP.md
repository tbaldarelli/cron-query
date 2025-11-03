# Project Rules for cron-query

## Environment
- This project runs on Linux systems (migrated from Global rule)

## Development Preferences  
- Use Python for main product development
- Keep Go and Rust as options for future learning phases

## Additional Project-Specific Rules

### Git Commit Messages on Windows
When running git commit commands on Windows PowerShell:
- Always use single quotes around multi-line commit messages
- Embed newlines directly in the string using `\n`
- Format: `git commit -m "title\n\n- bullet point 1\n- bullet point 2"`
- Do NOT use double quotes or here-strings for commit messages
- This avoids PowerShell string parsing issues on Windows
