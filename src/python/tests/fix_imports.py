#!/usr/bin/env python3

import os
import re

def update_imports(file_path):
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # Update imports and patch decorators
    content = content.replace('src.cron_query', 'cron_query')
    
    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)

def main():
    for filename in os.listdir('.'):
        if filename.startswith('test_') and filename.endswith('.py'):
            print(f"Updating {filename}...")
            update_imports(filename)

if __name__ == '__main__':
    main()