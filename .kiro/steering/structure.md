# Project Structure

## Root Directory Organization

```
├── src/
│   ├── python/           # Python implementation
│   │   ├── cron_query/   # Main Python package
│   │   └── tests/        # Python tests
│   └── groovy/           # Groovy implementation
│       ├── cron-query/   # Main Groovy source
│       └── test/         # Groovy/Spock tests
├── docs/                 # Documentation
├── man/man1/            # Manual pages
├── .kiro/               # Kiro configuration and steering
├── .github/             # GitHub Actions workflows
└── build/               # Gradle build output
```

## Source Directory Conventions

### Python Structure (`src/python/`)
- **Package**: `cron_query` (underscore naming)
- **Tests**: Located in `src/python/tests/`
- **Entry Point**: `cron_query.main:main`
- **Module Organization**: Follow standard Python package structure

### Groovy Structure (`src/groovy/`)
- **Package**: `com.cronquery` (Java-style naming)
- **Main Source**: `src/groovy/cron-query/`
- **Tests**: `src/groovy/test/` using Spock framework
- **Entry Point**: `com.cronquery.Main`

## Build Configuration

### Python
- **Setup**: `setup.py` with package metadata
- **Dependencies**: `requirements.txt`
- **Version**: Managed by `.bumpversion.cfg`

### Groovy/Java
- **Build**: `build.gradle` with custom source sets
- **Wrapper**: `gradle/wrapper/` for consistent builds
- **Version**: Synchronized with Python version

## Documentation Structure

- **README.md**: Main project documentation
- **DEVELOPMENT.md**: Cross-platform development guidelines
- **CHANGELOG.md**: Version history and changes
- **man/man1/**: Unix manual pages
- **docs/**: Additional documentation

## Design Documentation

- **Spec-Driven Design**: `docs/spec-driven-design/` contains the original SDD process
  - `requirements.md`: Detailed functional and non-functional requirements
  - `design.md`: Technical architecture and component specifications
  - `tasks.md`: Implementation task breakdown with milestones
  - `learning_tasks.md`: Future learning implementations (Go, Rust, Node.js)

## Configuration Files

- **Version Management**: `.bumpversion.cfg` for coordinated versioning across implementations
- **Git**: `.gitignore`, `.gitattributes` for cross-platform compatibility
- **IDE**: `.vscode/` for VS Code configuration
- **Deployment**: `package_for_centos.py` for Linux packaging
- **Development**: `DEVELOPMENT.md` for cross-platform development practices

## Development Approach

**Spec-Driven Development Process**:
1. Requirements specification with clear success criteria
2. Technical design with component architecture
3. Task breakdown with time estimates and dependencies
4. Milestone-based implementation with testing phases
5. Learning implementations for technology exploration