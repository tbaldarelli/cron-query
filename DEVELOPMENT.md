# Development Guide

## Cross-Platform Development Practices

### Line Endings
- Git is configured to handle line endings via `.gitattributes`
- All text files use LF (`\n`) line endings for Linux compatibility
- Windows developers: Git will handle CRLF → LF conversion automatically

### File Paths
- Always use `os.path.join()` for building paths
- Use `os.path.sep` when building paths from root
- For system paths (like `/etc/crontab`), use `os.path.join(os.path.sep, "etc", "crontab")`

### Script Permissions
- Shell scripts (*.sh) are set to executable (755) during packaging
- The main runner script is set to executable (755) during packaging
- Windows developers: Permissions are handled by package_for_centos.py

### Development Environment
- Windows: Development with mock data
- Linux: Real crontab access
- Use package_for_centos.py for deployment packaging
- Test deployment script handles file permissions and line endings

### Testing Practices
- Run tests on Windows during development
- Test package on Linux before deployment
- Verify file permissions after deployment
- Check line endings on Linux after deployment

### Common Issues
1. **Line Endings**
   - If scripts fail on Linux with "bad interpreter", check line endings
   - Use `dos2unix` if needed: `dos2unix script.sh`

2. **File Permissions**
   - If scripts aren't executable, check umask settings
   - Run `chmod +x script.sh` if needed

3. **Path Separators**
   - Windows uses backslash `\`
   - Linux uses forward slash `/`
   - Use `os.path.join()` to handle this automatically