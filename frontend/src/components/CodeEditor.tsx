import CodeMirror from '@uiw/react-codemirror';
import { cpp } from '@codemirror/lang-cpp';
import { java } from '@codemirror/lang-java';
import { python } from '@codemirror/lang-python';
import { useMemo } from 'react';
import type { Language } from '../types';

interface CodeEditorProps {
  language: Language;
  value: string;
  onChange: (value: string) => void;
  readOnly?: boolean;
}

const LANGUAGE_EXTENSIONS = {
  JAVA: java,
  CPP: cpp,
  PYTHON: python,
} as const;

export function CodeEditor({ language, value, onChange, readOnly = false }: CodeEditorProps) {
  const extensions = useMemo(() => [LANGUAGE_EXTENSIONS[language]()], [language]);

  return (
    <div className="code-editor">
      <CodeMirror
        value={value}
        height="400px"
        extensions={extensions}
        onChange={onChange}
        readOnly={readOnly}
        basicSetup={{
          lineNumbers: true,
          highlightActiveLine: !readOnly,
          foldGutter: true,
        }}
      />
    </div>
  );
}
