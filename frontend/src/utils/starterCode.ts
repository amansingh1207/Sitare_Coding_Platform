import type { Language } from '../types';

export const SUPPORTED_LANGUAGES: { value: Language; label: string }[] = [
  { value: 'JAVA', label: 'Java' },
  { value: 'CPP', label: 'C++' },
  { value: 'PYTHON', label: 'Python' },
];

const STARTER_TEMPLATES: Record<Language, string> = {
  JAVA: `import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        // Write your solution here
    }
}
`,
  CPP: `#include <bits/stdc++.h>
using namespace std;

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    // Write your solution here
    return 0;
}
`,
  PYTHON: `import sys

def solve() -> None:
    data = sys.stdin.read().strip().split()
    if not data:
        return
    # Write your solution here
    pass

if __name__ == "__main__":
    solve()
`,
};

export function getStarterCode(language: Language): string {
  return STARTER_TEMPLATES[language];
}

export function isSupportedLanguage(value: string): value is Language {
  return value === 'JAVA' || value === 'CPP' || value === 'PYTHON';
}
