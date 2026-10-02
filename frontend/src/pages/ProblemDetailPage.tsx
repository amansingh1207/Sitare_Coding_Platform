import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { problemsApi } from '../api/problems';
import { submissionsApi } from '../api/submissions';
import { ApiError } from '../api/client';
import { CodeEditor } from '../components/CodeEditor';
import { PracticeTimer } from '../components/PracticeTimer';
import { TestCaseResults, statusLabel } from '../components/TestCaseResults';
import { SUPPORTED_LANGUAGES, getStarterCode, isSupportedLanguage } from '../utils/starterCode';
import type { Language, ProblemDetail, RunResult, SubmissionDetail } from '../types';

type ActionState =
  | { kind: 'idle' }
  | { kind: 'running' }
  | { kind: 'error'; message: string };

export function ProblemDetailPage() {
  const { slug = '' } = useParams();
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [language, setLanguage] = useState<Language>('JAVA');
  const [code, setCode] = useState(() => getStarterCode('JAVA'));
  const [runState, setRunState] = useState<ActionState>({ kind: 'idle' });
  const [runResult, setRunResult] = useState<RunResult | null>(null);
  const [submitState, setSubmitState] = useState<ActionState>({ kind: 'idle' });
  const [submission, setSubmission] = useState<SubmissionDetail | null>(null);

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

  const handleLanguageChange = useCallback((value: string) => {
    if (!isSupportedLanguage(value)) {
      return;
    }
    setLanguage(value);
    setCode(getStarterCode(value));
    setRunResult(null);
    setSubmission(null);
  }, []);

  const handleReset = useCallback(() => {
    setCode(getStarterCode(language));
  }, [language]);

  const handleRun = useCallback(async () => {
    if (!problem) {
      return;
    }
    setRunState({ kind: 'running' });
    setRunResult(null);
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
    try {
      const ref = await submissionsApi.submit({
        problemId: problem.id,
        language,
        sourceCode: code,
      });
      const judged = await submissionsApi.pollUntilJudged(ref.id);
      setSubmission(judged);
      setSubmitState({ kind: 'idle' });
    } catch (err) {
      setSubmitState({
        kind: 'error',
        message: err instanceof Error ? err.message : 'Submit failed',
      });
    }
  }, [problem, language, code]);

  if (loading) {
    return <p>Loading problem...</p>;
  }
  if (loadError || !problem) {
    return <p className="error">{loadError ?? 'Problem not found.'}</p>;
  }

  const busy = runState.kind === 'running' || submitState.kind === 'running';

  return (
    <div className="problem-detail-page">
      <div className="problem-detail__statement">
        <h2>{problem.title}</h2>
        <div className="problem-detail__meta">
          <span>{problem.difficulty}</span>
          <span>{problem.weekLabel}</span>
          <span>Time limit: {problem.timeLimitMs} ms</span>
          <span>Memory limit: {problem.memoryLimitMb} MB</span>
        </div>
        <div className="markdown">{problem.statement}</div>
        <h3>Input Format</h3>
        <div className="markdown">{problem.inputFormat}</div>
        <h3>Output Format</h3>
        <div className="markdown">{problem.outputFormat}</div>
        {problem.constraints && (
          <>
            <h3>Constraints</h3>
            <div className="markdown">{problem.constraints}</div>
          </>
        )}
        <h3>Sample Test Cases</h3>
        {problem.sampleTestCases.map((sample, index) => (
          <div key={sample.id} className="sample-case">
            <h4>Sample {index + 1}</h4>
            <div>
              <span>Input:</span>
              <pre>{sample.inputData}</pre>
            </div>
            <div>
              <span>Expected output:</span>
              <pre>{sample.expectedOutput}</pre>
            </div>
          </div>
        ))}
      </div>

      <div className="problem-detail__editor">
        <div className="editor-toolbar">
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
          <button type="button" onClick={handleReset} disabled={busy}>
            Reset
          </button>
          <button type="button" onClick={handleRun} disabled={busy}>
            {runState.kind === 'running' ? 'Running...' : 'Run Code'}
          </button>
          <button type="button" onClick={handleSubmit} disabled={busy}>
            {submitState.kind === 'running' ? 'Submitting...' : 'Submit Code'}
          </button>
        </div>

        <CodeEditor language={language} value={code} onChange={setCode} />

        <PracticeTimer problemTitle={problem.title} />

        {runState.kind === 'error' && <p className="error">{runState.message}</p>}
        {runResult && (
          <>
            <p className="run-status">
              Run result: <strong>{statusLabel(runResult.status)}</strong>
            </p>
            <TestCaseResults results={runResult.testResults} />
          </>
        )}

        {submitState.kind === 'error' && <p className="error">{submitState.message}</p>}
        {submission && (
          <div className="submission-result">
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
      </div>
    </div>
  );
}
