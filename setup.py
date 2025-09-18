#!/usr/bin/env python3

from setuptools import setup, find_packages
import os

# Read the contents of requirements.txt
def read_requirements():
    with open('requirements.txt', 'r') as f:
        return [line.strip() for line in f if line.strip() and not line.startswith('#')]

# Read the contents of README.md (when it exists)
def read_long_description():
    readme_path = 'README.md'
    if os.path.exists(readme_path):
        with open(readme_path, 'r', encoding='utf-8') as f:
            return f.read()
    return "A command-line tool for querying crontab schedules with natural language"

setup(
    name="cron-query",
    version="1.0.0",
    author="Anthony Baldarelli",
    author_email="tony.baldarelli@gmail.com",
    description="Query crontab schedules with natural language",
    long_description=read_long_description(),
    long_description_content_type="text/markdown",
    url="https://github.com/tonybaldarelli/cron-query",  # Update when you create the repo
    packages=find_packages(where="src"),
    package_dir={"": "src"},
    classifiers=[
        "Development Status :: 3 - Alpha",
        "Intended Audience :: System Administrators",
        "Intended Audience :: Developers",
        "License :: OSI Approved :: MIT License",
        "Operating System :: POSIX :: Linux",
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.7",
        "Programming Language :: Python :: 3.8",
        "Programming Language :: Python :: 3.9",
        "Programming Language :: Python :: 3.10",
        "Topic :: System :: Systems Administration",
        "Topic :: Utilities",
    ],
    python_requires=">=3.7",
    install_requires=[
        "croniter>=1.0.0",
        "python-dateutil>=2.8.0",
        "colorama>=0.4.3",
        "tabulate>=0.8.7",
        "pyyaml>=5.3.1",  # Optional for YAML output
    ],
    extras_require={
        "dev": [
            "pytest>=6.0.0",
            "pytest-cov>=2.10.0",
        ],
    },
    entry_points={
        "console_scripts": [
            "cron-query=cron_query.main:main",
        ],
    },
    keywords="cron crontab schedule query linux sysadmin",
    project_urls={
        "Bug Reports": "https://github.com/tonybaldarelli/cron-query/issues",
        "Source": "https://github.com/tonybaldarelli/cron-query",
    },
    data_files=[
        ('share/man/man1', ['man/man1/cron-query.1']),
    ],
)
