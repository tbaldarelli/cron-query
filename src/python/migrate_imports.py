#!/usr/bin/env python3

import os
import re

def update_imports(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    
    new_lines = []
    for line in lines:
        # Update imports
        if 'from src.cron_query' in line:
            line = line.replace('from src.cron_query', 'from cron_query')
        # Update patch decorators
        if '@patch(' in line:
            line = line.replace("'src.cron_query", "'cron_query")
        new_lines.append(line)
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.writelines(new_lines)

def main():
    tests_dir = 'tests'
    for filename in os.listdir(tests_dir):
        if filename.startswith('test_') and filename.endswith('.py'):
            file_path = os.path.join(tests_dir, filename)
            print(f"Updating {filename}...")
            update_imports(file_path)

if __name__ == '__main__':
    main()