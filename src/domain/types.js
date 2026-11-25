export class DomainError extends Error {
  code;
  constructor(code) {
    super(code);
    this.code = code;
    this.name = 'DomainError';
  }
}
