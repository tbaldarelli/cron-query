# Installing cron-query on CentOS

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
