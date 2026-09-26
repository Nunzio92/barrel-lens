# Changelog

## [Unreleased]

## [0.1.0] - 2026-09-26

### Added

- Gutter icon on TypeScript declarations (functions, `const`/`let` bindings, type aliases,
  interfaces) that are re-exported through a barrel (`index.ts`/`index.tsx`) file, making a
  module's public API visible at a glance in modular monorepos.
- Click the icon to jump directly to the re-export line in the barrel file.
- Resolution follows star exports, named exports, and aliased/type-only re-exports through the
  platform's own module resolver, across nested barrel directories.
