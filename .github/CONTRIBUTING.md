# Contributing to CertAlert

Thank you for your interest in contributing to Certalert! We welcome all contributions, whether they are bug fixes, feature enhancements, or documentation improvements.

## Getting Started

1. **Fork the Repository**: Click the "Fork" button at the top of the repository page.
2. **Clone Your Fork**:
   ```sh
   git clone https://github.com/your-username/certalert.git
   cd certalert
   ```
3. **Create a New Branch**:
   ```sh
   git checkout -b feature-branch-name
   ```
4. **Set Up the Development Environment**:
   - Ensure you have gradle installed.
   - Install dependencies using:
     ```sh
     ./gradlew dependencies
     ```
   - Run tests to confirm everything is working:
     ```sh
     ./gradlew test
     ```

## Contribution Guidelines

### Code Contributions

- Follow idiomatic Go best practices.
- Ensure all changes are covered by unit tests.
- Keep functions small and maintainable.
- Follow the project’s logging and error-handling patterns.
- Avoid introducing breaking changes unless absolutely necessary.
- Use meaningful commit messages and describe the changes clearly.

### Testing

- Run tests locally before submitting a PR.
- Add tests for new features and bug fixes.
- Ensure all tests pass using:
  ```sh
  ./gradlew test
  ```

## Submitting a Pull Request

1. **Commit Your Changes**:
   ```sh
   git add .
   git commit -m "Descriptive commit message"
   ```
2. **Push Your Branch**:
   ```sh
   git push origin feature-branch-name
   ```
3. **Open a Pull Request**:
   - Go to the main repository on GitHub.
   - Click on "New pull request".
   - Select your branch and describe the changes.
   - Submit the pull request.

## Issues and Feature Requests

- Before opening a new issue, check if it has already been reported.
- Provide as much detail as possible when submitting an issue.
- When requesting a feature, describe the use case and potential implementation details.

## Questions?

If you have any questions, feel free to open an issue or start a discussion in the repository.

Happy coding! 🚀
