# Cron-Query Tool - Implementation Tasks

## Phase 1: MVP Core Functionality

### Task 1: Project Setup
- [x] Create Python package structure (`src/cron_query/`)
- [x] Set up `requirements.txt` with dependencies (`croniter`, `argparse`)
- [x] Create `setup.py` for package installation
- [x] Initialize basic CLI entry point (`main.py`)
- [x] Set up basic logging configuration

**Estimated Time**: 1-2 hours  
**Dependencies**: None  
**Priority**: High

### Task 2: Cron Data Loader (`cron_loader.py`)
- [x] Implement `CronJob` data class
- [x] Create `load_user_crontab()` function (mock for Windows development)
- [x] Create `parse_cron_line()` function to handle cron syntax
- [x] Handle special cron keywords (`@daily`, `@weekly`, etc.)
- [x] Basic error handling for invalid cron expressions
- [x] Unit tests with sample crontab data

**Estimated Time**: 3-4 hours  
**Dependencies**: Task 1  
**Priority**: High

### Task 3: Query Parser (`query_parser.py`)
- [x] Implement `QueryCriteria` data class
- [x] Create `parse_day_query()` for day-based queries
  - "Saturday", "Sunday", etc.
  - "weekdays", "weekends"
- [x] Create `parse_time_query()` for time-based queries
  - "8 AM", "8:30 PM", "20:30"
- [x] Basic query validation and error handling
- [x] Unit tests for each query type

**Estimated Time**: 4-5 hours  
**Dependencies**: Task 1  
**Priority**: High

### Task 4: Schedule Analyzer (`schedule_analyzer.py`)
- [x] Implement core matching logic using `croniter`
- [x] Create `runs_on_day_of_week()` function
- [x] Create `runs_at_time()` function
- [x] Handle day-of-month OR day-of-week logic correctly
- [x] Implement `get_next_runs()` for schedule preview
- [x] Comprehensive unit tests with edge cases

**Estimated Time**: 5-6 hours  
**Dependencies**: Task 2  
**Priority**: High

### Task 5: Output Formatter (`output_formatter.py`)
- [x] Implement basic list format output
- [x] Add human-readable cron expression descriptions
- [x] Show next run times for matched jobs
- [x] Handle empty results gracefully
- [x] Basic formatting tests
- [x] Implement table format output
- [x] Implement JSON format output
- [x] Add comprehensive error handling
- [x] Add utility functions for format validation

**Estimated Time**: 2-3 hours  
**Dependencies**: Task 4  
**Priority**: High

### Task 6: CLI Integration (`main.py`) ✅
- [x] Set up argument parsing with `argparse`
- [x] Connect all components in main execution flow
- [x] Add `--help` documentation with examples
- [x] Basic error handling and user feedback
- [x] Integration testing with sample data
- [x] Implement `--format` option (list|table|json)
- [x] Add `--verbose` mode with additional details
- [x] Add `--source` option for future extensibility
- [x] Output format validation and error handling
- [x] **BONUS**: `--file` option to analyze crontab files
- [x] **BONUS**: `load_crontab_from_file()` function with robust parsing
- [x] **BONUS**: File takes precedence over source when specified
- [x] **BONUS**: Comprehensive file loading tests and error handling

**Estimated Time**: 3-4 hours (completed with bonus file feature)  
**Dependencies**: Tasks 2-5  
**Priority**: High

## Phase 2: Enhanced Features

### Task 7: Advanced Query Support ✅
- [x] Support "this Saturday" vs "any Saturday"
- [x] Handle specific date queries (relative dates like "this Saturday", "next Monday")
- [x] Support time ranges ("between 9 AM and 5 PM", "after 10 AM", "before 5 PM")
- [x] Add date calculation utilities
- [x] Complex scenario support:
  - [x] "Which jobs run between 9 AM and 5 PM on weekdays?"
  - [x] "Which jobs run this Friday vs any Friday?"
  - [x] "Which jobs run this Saturday after 10 AM?"
  - [x] Combined day and time range queries
  - [ ] "Which jobs run on the first of the month?" (DOM-specific, future enhancement)
  - [ ] "Which jobs run on the first Monday of every month?" (complex DOM+DOW, future)
- [x] Handle DOM vs DOW intersection logic for combined queries
- [x] Extended test coverage (20 comprehensive tests)
- [x] **BONUS**: Typo tolerance ("comming" vs "coming")
- [x] **BONUS**: Punctuation handling (commas, complex formatting)
- [x] **BONUS**: 12-hour and 24-hour time format support
- [x] **BONUS**: Wildcard cron field parsing (*/15, ranges, steps)

**Estimated Time**: 4-5 hours (completed with significant bonus features)  
**Dependencies**: Task 6  
**Priority**: Medium  
**Status**: ✅ COMPLETED - Resolves CentOS issue with "jobs this comming Saturday, after 10 am"

### Task 8: Advanced Output Features ✅
- [x] Add color-coded output for terminal display
- [x] Implement custom output templates
- [x] Add export capabilities (CSV, YAML)
- [x] Enhanced verbose mode with cron parsing details
- [x] Output pagination for large result sets
- [x] **BONUS**: Template help and listing functionality (`--list-templates`, `--template-help`)
- [x] **BONUS**: Predefined templates (compact, detailed, summary, verbose)
- [x] **BONUS**: Color configuration with terminal detection
- [x] **BONUS**: Comprehensive CLI options for all features

**Estimated Time**: 3-4 hours (completed with significant bonus features)  
**Dependencies**: Task 6  
**Priority**: Low  
**Status**: ✅ COMPLETED - Full advanced output feature set implemented

### Task 9: System Crontab Support ✅
- [x] Implement `load_system_crontabs()` (Linux-specific)
- [x] Handle `/etc/crontab` parsing
- [x] Support `/etc/cron.d/*` directory scanning
- [x] Add `--source` option (user|system|all)
- [x] Permission handling and error reporting

**Estimated Time**: 4-5 hours  
**Dependencies**: Task 2  
**Priority**: Medium

### Task 10: Linux Integration & Testing
- [x] Test on actual CentOS system via SSH
- [x] Validate real crontab parsing
- [x] Performance testing with large crontab files
- [x] Cross-platform compatibility fixes
- [x] Documentation updates

**Estimated Time**: 3-4 hours  
**Dependencies**: Task 9  
**Priority**: Medium

## Phase 3: Polish & Documentation

### Task 11: Cross-Platform Compatibility ✅
- [x] Add `.gitattributes` for consistent line endings
- [x] Update packaging script to force Unix line endings
- [x] Test deployment workflow from Windows → Linux
- [x] Document cross-platform development practices
- [x] Validate script execution permissions on Linux
- [x] Test path handling across platforms

**Estimated Time**: 2-3 hours  
**Dependencies**: Task 10  
**Priority**: High (Essential for Linux deployment)

### Task 12: Comprehensive Testing
- [ ] Achieve >90% test coverage
  - Add tests for uncovered code paths
  - Create reusable test fixtures as needed
- [ ] Test parsing robustness with real-world crontab formats
  - Mixed user/system formats
  - Unusual comment formats
  - Malformed entries
- [ ] Edge case testing with focus on leap years
  - Test Feb 29th scheduling
  - Test year boundary conditions
  - Create mock data for date-specific scenarios

**Estimated Time**: 4-5 hours  
**Dependencies**: Tasks 1-11
**Priority**: Medium

### Task 13: Documentation & Packaging
- [ ] Complete `README.md` with usage examples
- [ ] Add installation instructions
- [ ] Create man page or extended help
- [ ] Package for easy distribution
- [ ] Version tagging and release notes

**Estimated Time**: 2-3 hours  
**Dependencies**: Task 12  
**Priority**: Low

## Phase 4: Learning Implementations (Future)

*Note: Learning tasks have been moved to a separate `learning_tasks.md` file.*

### Task 14: Context-Aware Next Runs Enhancement
- [ ] Enhance "Next runs" display to show runs on the queried date/context
- [ ] For specific date queries ("this Saturday"), prioritize Saturday runs over chronological
- [ ] For time range queries ("after 6 PM"), show next runs within that time range
- [ ] Add "Next runs on [date]" vs "Next runs overall" distinction in output
- [ ] Update output formatter to calculate context-relevant schedules
- [ ] Maintain backward compatibility with existing next run behavior
- [ ] Add tests for context-aware scheduling display

Natural language query improvements (from Task 10 testing):
- [ ] Support frequency keywords like "daily" (map to DOW/DOM-agnostic schedules)
- [ ] Accept numeric day-of-week (0-6, with 0/7 as Sunday)
- [ ] Handle three-letter day abbreviations (e.g., Sun, Mon, Tue)
- [ ] Improve handling for broad queries like "at every time" or "what jobs run daily"

**Example Enhancement**:
Current: `0 * * * *` shows "Next runs: 2025-09-16 19:00, 2025-09-16 20:00"
Enhanced: Query "this Saturday after 6 PM" shows "Next runs on Saturday: 2025-09-20 19:00, 2025-09-20 20:00"

**Estimated Time**: 2-3 hours  
**Dependencies**: Task 7  
**Priority**: Low (UX Enhancement)

## Development Milestones

### Milestone 1: Basic Functionality (Tasks 1-6)
- Can query user crontab on Linux
- Supports basic day and time queries
- Clear, readable output
- **Target**: End of Week 1

### Milestone 2: Enhanced Features (Tasks 7-10)  
- [x] Advanced query support (Task 7 - COMPLETED ✅)
- [x] Advanced output features (Task 8 - COMPLETED ✅)
- [x] System crontab integration (Task 9-10 - COMPLETED ✅)
- **Target**: End of Week 2
- **Progress**: 100% complete (4/4 tasks done)

### Milestone 3: Production Ready (Tasks 11-13)
- Cross-platform compatibility
- Comprehensive testing
- Complete documentation
- Ready for distribution
- **Target**: End of Week 3

### Milestone 4: Learning Phase
- Alternative implementations (see `learning_tasks.md`)
- Performance comparisons
- **Target**: Ongoing learning project

## Risk Mitigation

### High Risk Items
- **croniter complexity**: May need deep dive into day-of-month OR day-of-week logic
- **Natural language parsing**: Keep simple initially, avoid over-engineering
- **Cross-platform testing**: Need CentOS access for realistic testing

### Mitigation Strategies
- Start with simple query patterns
- Create comprehensive test fixtures
- Mock Linux behavior for initial Windows development
- Plan SSH testing sessions on CentOS

---

**Document Version**: 1.1  
**Last Updated**: 2025-09-16  
**Status**: Phase 2 In Progress - Task 7 Complete
