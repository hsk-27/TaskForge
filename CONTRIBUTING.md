# Contributing to TaskForge

Thank you for your interest in contributing to **TaskForge**! We welcome contributions from engineers, researchers, and distributed systems enthusiasts.

---

## Code of Conduct

We are committed to providing a welcoming, inclusive, and harassment-free environment for everyone. Please be respectful and constructive in all discussions, pull requests, and issue reports.

---

## Development Setup

### Prerequisites
- **Java Development Kit (JDK):** Version 21 LTS or newer.
- **Apache Maven:** Version 3.9+ (`mvn`).
- **Node.js & npm:** Node.js 20 LTS and npm 10+.
- **Docker & Docker Compose:** Optional for running full-stack containerized deployments.

### Local Environment Setup
1. Fork the repository and clone your fork:
   ```bash
   git clone https://github.com/your-username/taskforge.git
   cd taskforge
   ```
2. Build and verify the backend:
   ```bash
   cd backend
   mvn clean test
   ```
3. Install frontend dependencies and verify tests:
   ```bash
   cd ../frontend
   npm install
   npm test -- --run
   npm run build
   ```

---

## Submitting Pull Requests

1. **Create a Topic Branch:**
   ```bash
   git checkout -b feature/my-enhancement
   ```
2. **Follow Coding Standards:**
   - **Backend:** Follow standard Java naming conventions and clean domain-driven architecture. Avoid running computations inside `@Transactional` methods (maintain two-phase transaction decoupling).
   - **Frontend:** Maintain TypeScript typing; avoid `any` wherever possible. Adhere to the established Tailwind slate aesthetic (`font-sans` copy, `font-mono` for hashes/numbers, calm status indicators).
3. **Verify All Tests Pass:**
   Ensure both backend and frontend tests pass before opening a PR:
   ```bash
   # In backend/
   mvn test
   # In frontend/
   npm test -- --run
   ```
4. **Write Clear Commit Messages:**
   Use conventional commits: `feat:`, `fix:`, `docs:`, `test:`, or `refactor:`.

---

## Reporting Issues

If you find a bug or have a suggestion, please open an issue using the appropriate template:
- **Bug Report:** Include clear reproduction steps, expected vs. actual behavior, and environment details.
- **Feature Request:** Describe the problem you are solving and the proposed behavior.
