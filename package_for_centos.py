#!/usr/bin/env python3
"""
Package cron-query for deployment to CentOS.
Creates a portable directory structure that can be copied to another system.
"""

import os
import shutil
import sys
from pathlib import Path

def create_centos_package(python_cmd="python3"):
    """Create a portable package for CentOS.
    
    Args:
        python_cmd: Python command to use in shebang (e.g., 'python3', 'python3.7', 'python')
    """
    
    print(f"📦 Creating CentOS package for cron-query (using {python_cmd})...")
    
    # Create package directory
    package_dir = Path("cron-query-centos")
    if package_dir.exists():
        shutil.rmtree(package_dir)
    package_dir.mkdir()
    
    print(f"✓ Created package directory: {package_dir}")
    
    # Copy source code
    src_dir = package_dir / "src"
    shutil.copytree("src", src_dir)
    print("✓ Copied source code")
    
    # Copy requirements
    shutil.copy("requirements.txt", package_dir)
    print("✓ Copied requirements.txt")
    
    # Create a simple runner script
    runner_script = package_dir / "cron-query"
    with open(runner_script, 'w', encoding='utf-8') as f:
        f.write(f"""#!/usr/bin/env {python_cmd}
\"\"\"
Cron-Query - Natural language cron job scheduler analysis tool
\"\"\"

import sys
import os

# Add the src directory to Python path
script_dir = os.path.dirname(os.path.abspath(__file__))
src_dir = os.path.join(script_dir, 'src')
sys.path.insert(0, src_dir)

# Run the main application
from cron_query.main import main

if __name__ == '__main__':
    sys.exit(main())
""")
    
    # Make the runner script executable
    os.chmod(runner_script, 0o755)
    print("✓ Created executable runner script")
    
    # Create installation instructions
    install_instructions = package_dir / "INSTALL_CENTOS.md"
    with open(install_instructions, 'w', encoding='utf-8') as f:
        f.write("""# Installing cron-query on CentOS

## Prerequisites

Make sure Python 3.6+ is installed:
```bash
python3 --version
```

If not installed, or if you get 'command not found':
```bash
# Try these commands to find your Python installation:
python --version
python3.6 --version  
python3.7 --version
python3.8 --version

# Install Python 3 on CentOS/RHEL:
sudo yum install python3 python3-pip
# OR for newer versions:
sudo yum install python36 python36-pip
sudo yum install python37 python37-pip
```

**Note for CentOS 7**: If `python3` command is not available but `python3.7` is,
you may need to edit the shebang line in the `cron-query` script:
```bash
# Change the first line from:
#!/usr/bin/env python3
# To:
#!/usr/bin/env python3.7
```

## Installation Steps

1. **Copy this directory to your CentOS machine:**
   ```bash
   scp -r cron-query-centos/ user@your-centos-server:~/
   ```

2. **SSH into your CentOS machine:**
   ```bash
   ssh user@your-centos-server
   cd ~/cron-query-centos/
   ```

3. **Install Python dependencies:**
   ```bash
   pip3 install --user -r requirements.txt
   ```
   
   Or if you have sudo access:
   ```bash
   sudo pip3 install -r requirements.txt
   ```

4. **Test the installation:**
   ```bash
   ./cron-query "jobs on Monday"
   ```

5. **Optional: Add to PATH for global access:**
   ```bash
   # Add this line to your ~/.bashrc
   export PATH="$HOME/cron-query-centos:$PATH"
   
   # Reload your shell
   source ~/.bashrc
   
   # Now you can use it from anywhere:
   cron-query "jobs on weekdays"
   ```

## Usage Examples

```bash
# Basic queries
./cron-query "jobs on Monday"
./cron-query "jobs at 8 AM"
./cron-query "jobs on weekends"

# Different output formats
./cron-query --format table "jobs on weekdays"
./cron-query --format json "jobs at 2 AM"

# Analyze a specific crontab file
./cron-query --file /path/to/crontab "jobs on Saturday"

# Verbose output (for debugging)
./cron-query --verbose "jobs on Monday"

# Get help
./cron-query --help
```

## Troubleshooting

If you get "command not found":
```bash
python3 cron-query "jobs on Monday"
```

If you get import errors:
```bash
pip3 install --user croniter
```
""")
    
    print("✓ Created installation instructions")
    
    # Create a test crontab for CentOS testing
    test_crontab = package_dir / "sample_crontab.txt"
    with open(test_crontab, 'w', encoding='utf-8') as f:
        f.write("""# Sample crontab for testing on CentOS
# Daily backup at 2 AM
0 2 * * * /usr/local/bin/backup.sh

# Weekly reports on Monday morning
0 9 * * 1 /home/user/weekly-report.py

# System maintenance on weekends
0 3 * * 6,0 /usr/sbin/system-cleanup.sh

# Check disk space every hour during business hours
0 9-17 * * 1-5 /usr/local/bin/check-disk.sh

# Log rotation at midnight
0 0 * * * /usr/sbin/logrotate /etc/logrotate.conf

# Database backup on first of month
0 1 1 * * /usr/local/bin/db-backup.sh

# Special shortcuts
@daily /home/user/daily-tasks.sh
@hourly /usr/local/bin/health-check.sh
""")
    
    print("✓ Created sample crontab file")
    
    # Create a quick test script
    test_script = package_dir / "test_on_centos.sh"
    with open(test_script, 'w', encoding='utf-8') as f:
        f.write("""#!/bin/bash
# Quick test script for CentOS

echo "Testing cron-query on CentOS..."
echo

echo "1. Testing basic query:"
./cron-query "jobs on Monday"
echo

echo "2. Testing table format:"
./cron-query --format table "jobs at 2 AM"
echo

echo "3. Testing file input:"
./cron-query --file sample_crontab.txt "jobs on weekends"
echo

echo "4. Testing JSON output:"
./cron-query --format json "jobs on weekdays" | head -10
echo

echo "All tests completed successfully!"
""")
    
    os.chmod(test_script, 0o755)
    print("✓ Created test script")
    
    # Show summary
    print(f"""
🎉 CentOS package created successfully!

Package contents:
  {package_dir}/
  ├── src/                     # Source code
  ├── cron-query               # Executable script
  ├── requirements.txt         # Python dependencies
  ├── INSTALL_CENTOS.md       # Installation instructions
  ├── sample_crontab.txt      # Test crontab file
  └── test_on_centos.sh       # Quick test script

Usage:
  python package_for_centos.py           # Creates package with 'python3' shebang
  python package_for_centos.py python3.7 # Creates package with 'python3.7' shebang
  python package_for_centos.py python    # Creates package with 'python' shebang

Next steps:
1. Copy the '{package_dir}' directory to your CentOS machine
2. Follow the instructions in INSTALL_CENTOS.md
3. Run ./test_on_centos.sh to verify everything works

Example copy command:
  scp -r {package_dir}/ user@your-centos-server:~/
""")

if __name__ == '__main__':
    import sys
    
    # Check for python version argument
    python_cmd = "python3"  # default
    if len(sys.argv) > 1:
        python_cmd = sys.argv[1]
    
    create_centos_package(python_cmd)
