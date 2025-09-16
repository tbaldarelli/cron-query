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

### Task 7: Advanced Query Support
- [ ] Support "this Saturday" vs "any Saturday"
- [ ] Handle specific date queries ("January 15th")
- [ ] Support time ranges ("between 9 AM and 5 PM")
- [ ] Add date calculation utilities
- [ ] Complex scenario support:
  - "Which jobs run on the first of the month?"
  - "Which jobs run on the first of the month at 8 AM?"
  - "Which jobs run on Mon, Wed, Fri at 8 AM?"
  - "Which jobs run on the first Monday of every month?"
  - "Which jobs that run Mon/Wed/Fri would run on the first of this month?"
  - "Which jobs run between 9 AM and 5 PM on weekdays?"
  - "Which jobs run this Friday vs any Friday?"
- [ ] Handle DOM vs DOW intersection logic for complex queries
- [ ] Extended test coverage

**Estimated Time**: 4-5 hours  
**Dependencies**: Task 6  
**Priority**: Medium

### Task 8: Advanced Output Features
- [ ] Add color-coded output for terminal display
- [ ] Implement custom output templates
- [ ] Add export capabilities (CSV, YAML)
- [ ] Enhanced verbose mode with cron parsing details
- [ ] Output pagination for large result sets

**Estimated Time**: 3-4 hours  
**Dependencies**: Task 6  
**Priority**: Low

### Task 9: System Crontab Support
- [ ] Implement `load_system_crontabs()` (Linux-specific)
- [ ] Handle `/etc/crontab` parsing
- [ ] Support `/etc/cron.d/*` directory scanning
- [ ] Add `--source` option (user|system|all)
- [ ] Permission handling and error reporting

**Estimated Time**: 4-5 hours  
**Dependencies**: Task 2  
**Priority**: Medium

### Task 10: Linux Integration & Testing
- [ ] Test on actual CentOS system via SSH
- [ ] Validate real crontab parsing
- [ ] Performance testing with large crontab files
- [ ] Cross-platform compatibility fixes
- [ ] Documentation updates

**Estimated Time**: 3-4 hours  
**Dependencies**: Task 9  
**Priority**: Medium

## Phase 3: Polish & Documentation

### Task 11: Comprehensive Testing
- [ ] Achieve >90% test coverage
- [ ] Add integration tests with real cron data
- [ ] Performance benchmarking
- [ ] Edge case testing (leap years, timezone changes)
- [ ] Test data fixtures and helpers

**Estimated Time**: 4-5 hours  
**Dependencies**: Tasks 1-10  
**Priority**: Medium

### Task 12: Documentation & Packaging
- [ ] Complete `README.md` with usage examples
- [ ] Add installation instructions
- [ ] Create man page or extended help
- [ ] Package for easy distribution
- [ ] Version tagging and release notes

**Estimated Time**: 2-3 hours  
**Dependencies**: Task 11  
**Priority**: Low

## Phase 4: Learning Implementations (Future)

### Task 13: Go Implementation
- [ ] Research Go cron parsing libraries
- [ ] Implement equivalent functionality in Go
- [ ] Performance comparison with Python version
- [ ] Document lessons learned

**Estimated Time**: 8-10 hours  
**Dependencies**: Task 12  
**Priority**: Learning/Optional

### Task 14: Rust Implementation
- [ ] Research Rust cron parsing crates
- [ ] Implement equivalent functionality in Rust
- [ ] Memory safety and performance analysis
- [ ] Document lessons learned and comparisons

**Estimated Time**: 10-12 hours  
**Dependencies**: Task 13  
**Priority**: Learning/Optional

## Development Milestones

### Milestone 1: Basic Functionality (Tasks 1-6)
- Can query user crontab on Linux
- Supports basic day and time queries
- Clear, readable output
- **Target**: End of Week 1

### Milestone 2: Enhanced Features (Tasks 7-10)  
- Advanced query support
- Multiple output formats
- System crontab integration
- **Target**: End of Week 2

### Milestone 3: Production Ready (Tasks 11-12)
- Comprehensive testing
- Complete documentation
- Ready for distribution
- **Target**: End of Week 3

### Milestone 4: Learning Phase (Tasks 13-14)
- Alternative implementations
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

**Document Version**: 1.0  
**Last Updated**: 2025-09-12  
**Status**: Ready for Implementation