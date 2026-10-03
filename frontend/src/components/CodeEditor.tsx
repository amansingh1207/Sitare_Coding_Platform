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
        // Fill the pane instead of a fixed pixel box: a fixed height clips
        // the top lines when the layout shrinks and traps scroll inside a
        // nested container. 100% lets CodeMirror's own scroller own scrolling.
        height="100%"
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
