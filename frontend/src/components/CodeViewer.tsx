import CodeMirror from '@uiw/react-codemirror';
import { cpp } from '@codemirror/lang-cpp';
import { java } from '@codemirror/lang-java';
import { python } from '@codemirror/lang-python';
import { useMemo } from 'react';
import type { Language } from '../types';

const LANGUAGE_EXTENSIONS = {
  JAVA: java,
  CPP: cpp,
  PYTHON: python,
} as const;

interface CodeViewerProps {
  language: Language;
  code: string;
}

/**
 * Read-only, syntax-highlighted view of submitted source code.
 * Submitted code is displayed verbatim rather than executed here.
 */
export function CodeViewer({ language, code }: CodeViewerProps) {
  const extensions = useMemo(() => [LANGUAGE_EXTENSIONS[language]()], [language]);

  return (
    <div className="code-viewer">
      <CodeMirror
        value={code}
        height="360px"
        extensions={extensions}
        readOnly
        basicSetup={{
          lineNumbers: true,
          highlightActiveLine: false,
          foldGutter: true,
        }}
      />
    </div>
  );
}
