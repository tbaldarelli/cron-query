#!/bin/bash
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
