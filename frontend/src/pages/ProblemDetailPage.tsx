import { useCallback, useEffect, useRef, useState } from 'react';
import type { KeyboardEvent as ReactKeyboardEvent, PointerEvent as ReactPointerEvent } from 'react';
import { useParams } from 'react-router-dom';
import { problemsApi } from '../api/problems';
import { submissionsApi } from '../api/submissions';
import { ApiError } from '../api/client';
import { CodeEditor } from '../components/CodeEditor';
import { PracticeTimer } from '../components/PracticeTimer';
import { TestCaseResults, statusLabel } from '../components/TestCaseResults';
import { VerdictBanner } from '../components/VerdictBanner';
import {
  SUPPORTED_LANGUAGES,
  detectLanguageFromFileName,
  getStarterCode,
  isSupportedLanguage,
} from '../utils/starterCode';
import type {
  CustomRunResult,
  Language,
  ProblemDetail,
  RunResult,
  SubmissionDetail,
} from '../types';

type ActionState =
  | { kind: 'idle' }
  | { kind: 'running' }
  | { kind: 'error'; message: string };

type ConsoleTab = 'testcase' | 'result';

/** Backend rejects sources larger than this; the editor refuses them up front. */
const MAX_UPLOAD_BYTES = 256 * 1024;

interface Draft {
  language: Language;
  code: string;
}

function draftKey(slug: string): string {
  return `codingjudge_draft_${slug}`;
}

/** Reads a user-selected file, preferring Blob.text with a FileReader fallback. */
function readFileText(file: File): Promise<string> {
  if (typeof file.text === 'function') {
    return file.text().catch(
      () =>
        new Promise<string>((resolve, reject) => {
          const reader = new FileReader();
          reader.onload = () => resolve(String(reader.result ?? ''));
          reader.onerror = () => reject(reader.error ?? new Error('read failed'));
          reader.readAsText(file);
        }),
    );
  }
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(reader.error ?? new Error('read failed'));
    reader.readAsText(file);
  });
}

function loadDraft(slug: string): Draft | null {
  try {
    const raw = localStorage.getItem(draftKey(slug));
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as Partial<Draft>;
    if (!isSupportedLanguage(parsed.language ?? '') || typeof parsed.code !== 'string') {
      return null;
    }
    return { language: parsed.language as Language, code: parsed.code };
  } catch {
    return null;
  }
}

export function ProblemDetailPage() {
  const { slug = '' } = useParams();
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [language, setLanguage] = useState<Language>(() => loadDraft(slug)?.language ?? 'JAVA');
  const [code, setCode] = useState<string>(() => {
    const draft = loadDraft(slug);
    return draft?.code ?? getStarterCode(draft?.language ?? 'JAVA');
  });
  const [uploadError, setUploadError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [runState, setRunState] = useState<ActionState>({ kind: 'idle' });
  const [runResult, setRunResult] = useState<RunResult | null>(null);
  const [submitState, setSubmitState] = useState<ActionState>({ kind: 'idle' });
  const [submission, setSubmission] = useState<SubmissionDetail | null>(null);
  // Latest non-terminal poll while a submit is in flight; cleared on verdict.
  const [submitProgress, setSubmitProgress] = useState<SubmissionDetail | null>(null);
  const [customInput, setCustomInput] = useState('');
  const [customResult, setCustomResult] = useState<CustomRunResult | null>(null);
  const [customState, setCustomState] = useState<ActionState>({ kind: 'idle' });
  const [consoleTab, setConsoleTab] = useState<ConsoleTab>('testcase');

  // Resizable layout: description/editor split (%) and console height (px),
  // persisted so a refresh keeps the user's layout.
  const [leftPct, setLeftPct] = useState<number>(() => {
    const saved = Number(localStorage.getItem('codingjudge_pane_split'));
    return Number.isFinite(saved) && saved >= 20 && saved <= 80 ? saved : 50;
  });
  const [consoleH, setConsoleH] = useState<number>(() => {
    const saved = Number(localStorage.getItem('codingjudge_console_h'));
    return Number.isFinite(saved) && saved >= 140 && saved <= 640 ? saved : 240;
  });
  const workspaceRef = useRef<HTMLDivElement | null>(null);
  const dragRef = useRef<{
    kind: 'v' | 'h';
    startX: number;
    startY: number;
    startPct: number;
    startH: number;
  } | null>(null);

  useEffect(() => {
    try {
      localStorage.setItem('codingjudge_pane_split', String(leftPct));
    } catch {
      // Best-effort only.
    }
  }, [leftPct]);

  useEffect(() => {
    try {
      localStorage.setItem('codingjudge_console_h', String(consoleH));
    } catch {
      // Best-effort only.
    }
  }, [consoleH]);

  const onSplitterDown =
    (kind: 'v' | 'h') =>
    (e: ReactPointerEvent<HTMLDivElement>): void => {
      e.preventDefault();
      e.currentTarget.setPointerCapture(e.pointerId);
      dragRef.current = {
        kind,
        startX: e.clientX,
        startY: e.clientY,
        startPct: leftPct,
        startH: consoleH,
      };
    };

  const onSplitterMove = (e: ReactPointerEvent<HTMLDivElement>): void => {
    const drag = dragRef.current;
    if (!drag) {
      return;
    }
    if (drag.kind === 'v') {
      const width = workspaceRef.current?.getBoundingClientRect().width ?? 1;
      const next = drag.startPct + ((e.clientX - drag.startX) / width) * 100;
      setLeftPct(Math.min(80, Math.max(20, next)));
    } else {
      const next = drag.startH + (drag.startY - e.clientY);
      setConsoleH(Math.min(640, Math.max(140, next)));
    }
  };

  const endSplitterDrag = (): void => {
    dragRef.current = null;
  };

  const onSplitterKeyDown =
    (kind: 'v' | 'h') =>
    (e: ReactKeyboardEvent<HTMLDivElement>): void => {
      const step = kind === 'v' ? 2 : 20;
      if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') {
        e.preventDefault();
        if (kind === 'v') {
          setLeftPct((v) => Math.max(20, v - step));
        } else {
          setConsoleH((v) => Math.max(140, v - step));
        }
      } else if (e.key === 'ArrowRight' || e.key === 'ArrowUp') {
        e.preventDefault();
        if (kind === 'v') {
          setLeftPct((v) => Math.min(80, v + step));
        } else {
          setConsoleH((v) => Math.min(640, v + step));
        }
      } else if (e.key === 'Home') {
        e.preventDefault();
        if (kind === 'v') {
          setLeftPct(50);
        } else {
          setConsoleH(240);
        }
      }
    };

  useEffect(() => {
    setLoading(true);
    setLoadError(null);
    problemsApi
      .get(slug)
      .then(setProblem)
      .catch((err: unknown) =>
        setLoadError(err instanceof ApiError ? err.message : 'Failed to load problem'),
      )
      .finally(() => setLoading(false));
  }, [slug]);

  // Switching problems loads that problem's saved draft (or fresh starter
  // code) instead of leaking the previous problem's code into the editor.
  useEffect(() => {
    const draft = loadDraft(slug);
    const freshLanguage = draft?.language ?? 'JAVA';
    setLanguage(freshLanguage);
    setCode(draft?.code ?? getStarterCode(freshLanguage));
    setRunResult(null);
    setSubmission(null);
    setCustomResult(null);
    setRunState({ kind: 'idle' });
    setSubmitState({ kind: 'idle' });
    setCustomState({ kind: 'idle' });
    setUploadError(null);
  }, [slug]);

  // Autosave the draft so a refresh never loses code.
  useEffect(() => {
    try {
      localStorage.setItem(draftKey(slug), JSON.stringify({ language, code }));
    } catch {
      // Storage full or unavailable: drafting is best-effort only.
    }
  }, [slug, language, code]);

  const clearResults = useCallback(() => {
    setRunResult(null);
    setSubmission(null);
    setCustomResult(null);
    setRunState({ kind: 'idle' });
    setSubmitState({ kind: 'idle' });
    setCustomState({ kind: 'idle' });
  }, []);

  const handleLanguageChange = useCallback(
    (value: string) => {
      if (!isSupportedLanguage(value)) {
        return;
      }
      setLanguage(value);
      setCode(getStarterCode(value));
      clearResults();
    },
    [clearResults],
  );

  const handleReset = useCallback(() => {
    setCode(getStarterCode(language));
    clearResults();
  }, [language, clearResults]);

  const handleFileUpload = useCallback(
    async (file: File) => {
      setUploadError(null);
      if (file.size > MAX_UPLOAD_BYTES) {
        setUploadError(
          `File is too large (${Math.round(file.size / 1024)} KB). Maximum is 256 KB.`,
        );
        return;
      }
      try {
        const text = await readFileText(file);
        const detected = detectLanguageFromFileName(file.name);
        if (detected) {
          setLanguage(detected);
        }
        setCode(text);
        clearResults();
      } catch {
        setUploadError('Could not read the file. Please try again.');
      }
    },
    [clearResults],
  );

  const handleRun = useCallback(async () => {
    if (!problem) {
      return;
    }
    setRunState({ kind: 'running' });
    setRunResult(null);
    setConsoleTab('result');
    try {
      const result = await submissionsApi.runCode({
        problemId: problem.id,
        language,
        sourceCode: code,
      });
      setRunResult(result);
      setRunState({ kind: 'idle' });
    } catch (err) {
      setRunState({
        kind: 'error',
        message: err instanceof Error ? err.message : 'Run failed',
      });
    }
  }, [problem, language, code]);

  const handleSubmit = useCallback(async () => {
    if (!problem) {
      return;
    }
    setSubmitState({ kind: 'running' });
    setSubmission(null);
    setSubmitProgress(null);
    setConsoleTab('result');
    try {
      const ref = await submissionsApi.submit({
        problemId: problem.id,
        language,
        sourceCode: code,
      });
      const judged = await submissionsApi.pollUntilJudged(ref.id, setSubmitProgress);
      setSubmission(judged);
      setSubmitProgress(null);
      setSubmitState({ kind: 'idle' });
    } catch (err) {
      setSubmitState({
        kind: 'error',
        message: err instanceof Error ? err.message : 'Submit failed',
      });
    }
  }, [problem, language, code]);

  const handleCustomRun = useCallback(async () => {
    if (!problem) {
      return;
    }
    setCustomState({ kind: 'running' });
    setCustomResult(null);
    try {
      const result = await submissionsApi.runCustomInput({
        problemId: problem.id,
        language,
        sourceCode: code,
        stdin: customInput,
      });
      setCustomResult(result);
      setCustomState({ kind: 'idle' });
    } catch (err) {
      setCustomState({
        kind: 'error',
        message: err instanceof Error ? err.message : 'Custom run failed',
      });
    }
  }, [problem, language, code, customInput]);

  if (loading) {
    return <p>Loading problem...</p>;
  }
  if (loadError || !problem) {
    return <p className="error">{loadError ?? 'Problem not found.'}</p>;
  }

  const busy =
    runState.kind === 'running' ||
    submitState.kind === 'running' ||
    customState.kind === 'running';

  return (
    <div className="lc-workspace" ref={workspaceRef}>
      <section className="lc-pane lc-pane--left" style={{ flex: `0 0 ${leftPct}%` }}>
        <div className="lc-tabs" role="tablist" aria-label="Problem info">
          <button type="button" className="lc-tab lc-tab--active" role="tab" aria-selected="true">
            Description
          </button>
        </div>
        <div className="lc-pane__body">
          <h2 className="lc-problem-title">{problem.title}</h2>
          <div className="lc-badges">
            <span className={`diff diff--${problem.difficulty}`}>
              {problem.difficulty.charAt(0) + problem.difficulty.slice(1).toLowerCase()}
            </span>
            <span>{problem.weekLabel}</span>
            <span>Time limit: {problem.timeLimitMs} ms</span>
            <span>Memory limit: {problem.memoryLimitMb} MB</span>
          </div>
          <div className="markdown">{problem.statement}</div>
          {problem.sampleTestCases.map((sample, index) => (
            <div key={sample.id} className="lc-example">
              <h4>Example {index + 1}:</h4>
              <div>
                <span className="lc-example__label">Input:</span>
                <pre>{sample.inputData}</pre>
              </div>
              <div>
                <span className="lc-example__label">Output:</span>
                <pre>{sample.expectedOutput}</pre>
              </div>
            </div>
          ))}
          <h3>Input Format</h3>
          <div className="markdown">{problem.inputFormat}</div>
          <h3>Output Format</h3>
          <div className="markdown">{problem.outputFormat}</div>
          {problem.constraints && (
            <div className="lc-constraints">
              <h3>Constraints</h3>
              <div className="markdown">{problem.constraints}</div>
            </div>
          )}
        </div>
      </section>

      <div
        className="lc-splitter lc-splitter--v"
        role="separator"
        aria-orientation="vertical"
        aria-label="Resize description and editor panels"
        tabIndex={0}
        onPointerDown={onSplitterDown('v')}
        onPointerMove={onSplitterMove}
        onPointerUp={endSplitterDrag}
        onPointerCancel={endSplitterDrag}
        onKeyDown={onSplitterKeyDown('v')}
      />

      <section className="lc-pane lc-pane--right" style={{ flex: '1 1 0', minWidth: 0 }}>
        <div className="lc-editor-bar">
          <label>
            Language
            <select
              value={language}
              onChange={(e) => handleLanguageChange(e.target.value)}
              disabled={busy}
            >
              {SUPPORTED_LANGUAGES.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>
          <button
            type="button"
            className="btn btn-run"
            onClick={handleReset}
            disabled={busy}
            title="Restore starter code"
          >
            Reset
          </button>
          <button
            type="button"
            className="btn btn-run"
            onClick={() => fileInputRef.current?.click()}
            disabled={busy}
            title="Load code from a file on your system"
          >
            Upload File
          </button>
          <PracticeTimer problemTitle={problem.title} />
          <input
            ref={fileInputRef}
            type="file"
            accept=".java,.cpp,.c,.cc,.cxx,.py,.txt"
            aria-label="Upload code file"
            style={{ display: 'none' }}
            onChange={(e) => {
              const file = e.target.files?.[0];
              e.target.value = '';
              if (file) {
                void handleFileUpload(file);
              }
            }}
          />
        </div>
        {uploadError && (
          <p className="error" style={{ padding: '0 0.75rem' }}>
            {uploadError}
          </p>
        )}

        {/* Remount on problem/language switch so the editor always opens
            at line 1 with folds cleared instead of inheriting old scroll. */}
        <CodeEditor key={`${slug}:${language}`} language={language} value={code} onChange={setCode} />

        <div
          className="lc-splitter lc-splitter--h"
          role="separator"
          aria-orientation="horizontal"
          aria-label="Resize console panel"
          tabIndex={0}
          onPointerDown={onSplitterDown('h')}
          onPointerMove={onSplitterMove}
          onPointerUp={endSplitterDrag}
          onPointerCancel={endSplitterDrag}
          onKeyDown={onSplitterKeyDown('h')}
        />
        <div className="lc-console" style={{ height: consoleH }}>
          <div className="lc-tabs" role="tablist" aria-label="Console">
            <button
              type="button"
              role="tab"
              aria-selected={consoleTab === 'testcase'}
              className={consoleTab === 'testcase' ? 'lc-tab lc-tab--active' : 'lc-tab'}
              onClick={() => setConsoleTab('testcase')}
            >
              Testcase
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={consoleTab === 'result'}
              className={consoleTab === 'result' ? 'lc-tab lc-tab--active' : 'lc-tab'}
              onClick={() => setConsoleTab('result')}
            >
              Test Result
            </button>
          </div>
          <div className="lc-console__body">
            {consoleTab === 'testcase' && (
              <div className="custom-run">
                <p className="help-text">
                  Type your own input and run the program against it. Nothing is submitted or saved.
                </p>
                <textarea
                  value={customInput}
                  onChange={(e) => setCustomInput(e.target.value)}
                  rows={4}
                  placeholder={'e.g.\n3 4'}
                  disabled={busy}
                  aria-label="Custom input"
                />
                <div className="lc-actions">
                  <button
                    type="button"
                    className="btn btn-run"
                    onClick={handleCustomRun}
                    disabled={busy}
                  >
                    {customState.kind === 'running' ? 'Running...' : 'Run with Custom Input'}
                  </button>
                </div>
                {customState.kind === 'error' && <p className="error">{customState.message}</p>}
                {customResult && (
                  <div className="custom-run__result">
                    {customResult.unsupported ? (
                      <p className="help-text" data-testid="custom-run-unsupported">
                        {customResult.error ??
                          'Custom input runs are not supported by the current judge.'}
                      </p>
                    ) : (
                      <>
                        <p className="run-status">
                          Result: <strong>{statusLabel(customResult.status)}</strong>
                          {customResult.runtimeMs !== null && ` · ${customResult.runtimeMs} ms`}
                        </p>
                        <div>
                          <span>Output:</span>
                          <pre>{customResult.output ? customResult.output : '(no output)'}</pre>
                        </div>
                        {customResult.error && (
                          <div>
                            <span>Error:</span>
                            <pre>{customResult.error}</pre>
                          </div>
                        )}
                      </>
                    )}
                  </div>
                )}
              </div>
            )}
            {consoleTab === 'result' && (
              <div className="console-result">
                {runState.kind === 'error' && <p className="error">{runState.message}</p>}
                {submitState.kind === 'error' && <p className="error">{submitState.message}</p>}
                {submitState.kind === 'running' && submitProgress?.status === 'PENDING' && (
                  <p className="help-text" data-testid="submit-queue-progress">
                    {submitProgress.queuePosition != null
                      ? `In queue · position #${submitProgress.queuePosition}`
                      : 'In queue…'}
                  </p>
                )}
                {submitState.kind === 'running' && submitProgress?.status === 'JUDGING' && (
                  <p className="help-text" data-testid="submit-judging-progress">
                    Judging…
                  </p>
                )}
                {runResult && (
                  <>
                    <p className="run-status">
                      Run result: <strong>{statusLabel(runResult.status)}</strong>
                    </p>
                    <VerdictBanner
                      status={runResult.status}
                      results={runResult.testResults}
                      runtimeMs={runResult.totalRuntimeMs}
                      detail={runResult.compilationError}
                    />
                    <TestCaseResults results={runResult.testResults} />
                  </>
                )}
                {submission && (
                  <div className="submission-result">
                    <VerdictBanner
                      status={submission.status}
                      results={submission.testResults}
                      runtimeMs={submission.runtimeMs}
                      title={`Submission #${submission.id}`}
                    />
                    <h3>
                      Submission #{submission.id}: {statusLabel(submission.status)}
                    </h3>
                    <p>
                      {submission.runtimeMs !== null && `${submission.runtimeMs} ms`}
                      {submission.memoryUsedKb !== null && ` · ${submission.memoryUsedKb} KB`}
                    </p>
                    <TestCaseResults
                      results={submission.testResults}
                      title="Visible test results"
                    />
                  </div>
                )}
                {!runResult && !submission && runState.kind !== 'error' && submitState.kind !== 'error' && (
                  <p className="help-text">
                    Click Run Code or Submit Code to see results here.
                  </p>
                )}
              </div>
            )}
          </div>
        </div>

        <div className="lc-actions">
          <button
            type="button"
            className="btn btn-run"
            onClick={handleRun}
            disabled={busy}
          >
            {runState.kind === 'running' ? 'Running...' : 'Run Code'}
          </button>
          <button
            type="button"
            className="btn btn-submit"
            onClick={handleSubmit}
            disabled={busy}
          >
            {submitState.kind === 'running' ? 'Submitting...' : 'Submit Code'}
          </button>
        </div>
      </section>
    </div>
  );
}
