import { describe, expect, it } from 'vitest';
import { getStarterCode, isSupportedLanguage, SUPPORTED_LANGUAGES } from './starterCode';

describe('starterCode', () => {
  it('supports exactly Java, C++ and Python', () => {
    expect(SUPPORTED_LANGUAGES.map((l) => l.value)).toEqual(['JAVA', 'CPP', 'PYTHON']);
  });

  it('provides a Java template with a Main class', () => {
    const code = getStarterCode('JAVA');
    expect(code).toContain('class Main');
    expect(code).toContain('public static void main');
  });

  it('provides a C++ template with main', () => {
    const code = getStarterCode('CPP');
    expect(code).toContain('int main()');
    expect(code).toContain('#include');
  });

  it('provides a Python template with solve', () => {
    const code = getStarterCode('PYTHON');
    expect(code).toContain('def solve');
    expect(code).toContain('sys.stdin');
  });

  it('validates supported languages', () => {
    expect(isSupportedLanguage('JAVA')).toBe(true);
    expect(isSupportedLanguage('CPP')).toBe(true);
    expect(isSupportedLanguage('PYTHON')).toBe(true);
    expect(isSupportedLanguage('RUBY')).toBe(false);
    expect(isSupportedLanguage('')).toBe(false);
  });
});
