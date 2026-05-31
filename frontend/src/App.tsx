import { CSSProperties, FormEvent, ReactNode, useEffect, useMemo, useRef, useState } from 'react';
import {
  AlertTriangle,
  ArrowRightLeft,
  Bot,
  CheckCircle2,
  ClipboardCopy,
  ClipboardList,
  ExternalLink,
  FileCode2,
  Files,
  Github,
  GitPullRequestArrow,
  Loader2,
  SlidersHorizontal,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  Wrench,
  X,
} from 'lucide-react';
import { apiUrl } from './api';

type ReviewContext = {
  stats: ReviewContextStats;
  files: ReviewFileContext[];
  truncationNotes: string[];
  promptText: string;
};

type ReviewContextStats = {
  totalFiles: number;
  filesWithPatch: number;
  truncatedFiles: number;
  totalPatchCharacters: number;
  includedPatchCharacters: number;
  promptCharacters: number;
  maxPatchCharactersPerFile: number;
  maxPromptCharacters: number;
};

type ReviewFileContext = {
  filename: string;
  status: string;
  additions: number;
  deletions: number;
  changes: number;
  patchAvailable: boolean;
  truncated: boolean;
  originalPatchLength: number;
};

type PullRequestFile = {
  filename: string;
  status: string;
  additions: number;
  deletions: number;
  changes: number;
  patch: string;
  blobUrl: string | null;
  rawUrl: string | null;
  contentsUrl: string | null;
  previousFilename: string | null;
};

type AiRiskItem = {
  severity: string;
  file: string;
  title: string;
  detail: string;
  evidence: string;
  impact: string;
  confidence: string;
  needsHumanReview: boolean;
  recommendation: string;
};

type AiReview = {
  enabled: boolean;
  generated: boolean;
  providerId: string;
  modelId: string;
  summary: string;
  riskItems: AiRiskItem[];
  fileExplanations: FileExplanation[];
  requiredActions: string[];
  suggestions: string[];
  followUpItems: string[];
  limitations: string[];
  markdown: string;
  message: string;
};

type FileExplanation = {
  filename: string;
  explanation: string;
};

type PullRequestSummary = {
  owner: string;
  repository: string;
  pullNumber: number;
  normalizedUrl: string;
  title: string;
  author: string;
  state: string;
  draft: boolean;
  merged: boolean;
  headBranch: string;
  baseBranch: string;
  additions: number;
  deletions: number;
  changedFiles: number;
  htmlUrl: string;
  files: PullRequestFile[];
  reviewContext: ReviewContext;
  aiReview: AiReview | null;
};

type WorkbenchView = 'pr-info' | 'ai-review';

type ModelProvider = {
  id: string;
  displayName: string;
  baseUrl: string;
  modelId: string;
  apiKeyEnv: string;
  apiKeyAvailable: boolean;
};

type ModelConfigurationOptions = {
  defaultProviderId: string;
  providers: ModelProvider[];
};

type ModelFormState = {
  providerId: string;
  baseUrl: string;
  modelId: string;
  apiKey: string;
};

type ApiError = {
  code: string;
  message: string;
  timestamp: string;
};

type AiReviewStreamEvent = {
  type: 'chunk' | 'result' | 'error' | 'done';
  text?: string;
  review?: AiReview;
  message?: string;
};

const sampleUrl = 'https://github.com/klT45/Code-Review/pull/2';
const maxTrackItems = 12;
const emptyModelForm: ModelFormState = {
  providerId: '',
  baseUrl: '',
  modelId: '',
  apiKey: '',
};

export function App() {
  const [prUrl, setPrUrl] = useState('');
  const [summary, setSummary] = useState<PullRequestSummary | null>(null);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isReviewLoading, setIsReviewLoading] = useState(false);
  const [reviewStreamText, setReviewStreamText] = useState('');
  const [reviewError, setReviewError] = useState('');
  const [isWorkbenchOpen, setIsWorkbenchOpen] = useState(false);
  const [workbenchView, setWorkbenchView] = useState<WorkbenchView>('pr-info');
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'failed'>('idle');
  const [modelOptions, setModelOptions] = useState<ModelConfigurationOptions | null>(null);
  const [modelConfig, setModelConfig] = useState<ModelFormState>(emptyModelForm);
  const [modelConfigError, setModelConfigError] = useState('');
  const [isModelPanelOpen, setIsModelPanelOpen] = useState(false);
  const [githubToken, setGithubToken] = useState('');
  const [isGitHubPanelOpen, setIsGitHubPanelOpen] = useState(false);
  const analysisRunId = useRef(0);

  useEffect(() => {
    let isActive = true;

    async function loadModelOptions() {
      try {
        const response = await fetch(apiUrl('/api/model-config'));
        if (!response.ok) {
          const apiError = (await response.json()) as ApiError;
          throw new Error(apiError.message || '获取模型配置失败。');
        }

        const options = (await response.json()) as ModelConfigurationOptions;
        if (!isActive) {
          return;
        }

        const defaultProvider = options.providers.find((provider) => provider.id === options.defaultProviderId)
          ?? options.providers[0];
        setModelOptions(options);
        if (defaultProvider) {
          setModelConfig({
            providerId: defaultProvider.id,
            baseUrl: defaultProvider.baseUrl,
            modelId: defaultProvider.modelId,
            apiKey: '',
          });
        }
      } catch (caughtError) {
        if (isActive) {
          setModelConfigError(caughtError instanceof Error ? caughtError.message : '获取模型配置失败。');
        }
      }
    }

    loadModelOptions();

    return () => {
      isActive = false;
    };
  }, []);

  useEffect(() => {
    if (!isModelPanelOpen && !isGitHubPanelOpen && !isWorkbenchOpen) {
      return undefined;
    }

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setIsModelPanelOpen(false);
        setIsGitHubPanelOpen(false);
        setIsWorkbenchOpen(false);
      }
    }

    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [isGitHubPanelOpen, isModelPanelOpen, isWorkbenchOpen]);

  const changeSize = useMemo(() => {
    if (!summary) {
      return null;
    }
    return summary.additions + summary.deletions;
  }, [summary]);

  const fileStats = useMemo(() => {
    if (!summary) {
      return null;
    }

    const files = summary.files ?? [];
    const patchCount = files.filter((file) => file.patch).length;
    const largestFile = files.reduce<PullRequestFile | null>((largest, file) => {
      if (!largest || file.changes > largest.changes) {
        return file;
      }
      return largest;
    }, null);

    return {
      patchCount,
      largestFile,
      visibleTrackFiles: files.slice(0, maxTrackItems),
    };
  }, [summary]);

  const contextCoverage = useMemo(() => {
    if (!summary || summary.reviewContext.stats.totalPatchCharacters === 0) {
      return 0;
    }
    return Math.round(
      (summary.reviewContext.stats.includedPatchCharacters / summary.reviewContext.stats.totalPatchCharacters) * 100
    );
  }, [summary]);

  const reviewStats = useMemo(() => {
    const riskItems = summary?.aiReview?.riskItems ?? [];
    return {
      high: riskItems.filter((item) => item.severity === 'high').length,
      medium: riskItems.filter((item) => item.severity === 'medium').length,
      low: riskItems.filter((item) => item.severity === 'low').length,
      needsHumanReview: riskItems.filter((item) => item.needsHumanReview).length,
    };
  }, [summary]);

  const reviewMarkdown = useMemo(() => {
    if (!summary?.aiReview?.generated) {
      return '';
    }
    if (summary.aiReview.markdown?.trim()) {
      return summary.aiReview.markdown;
    }
    return buildReviewMarkdown(summary);
  }, [summary]);

  const markdownSourceLabel = summary?.aiReview?.markdown?.trim() ? '模型生成' : '结构化生成';

  const selectedProvider = useMemo(() => {
    return modelOptions?.providers.find((provider) => provider.id === modelConfig.providerId) ?? null;
  }, [modelConfig.providerId, modelOptions]);

  const modelReady = Boolean((modelConfig.apiKey.trim() || selectedProvider?.apiKeyAvailable)
    && modelConfig.baseUrl.trim()
    && modelConfig.modelId.trim());

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setReviewError('');
    setReviewStreamText('');
    setSummary(null);
    setIsWorkbenchOpen(false);
    setWorkbenchView('pr-info');
    setCopyState('idle');

    if (!prUrl.trim()) {
      setError('请输入 GitHub PR 链接。');
      return;
    }

    const runId = analysisRunId.current + 1;
    analysisRunId.current = runId;
    setIsLoading(true);
    setIsReviewLoading(true);
    try {
      const requestPayload = {
        prUrl: prUrl.trim(),
        modelConfig: buildModelConfigPayload(modelConfig),
        githubToken: githubToken.trim() || undefined,
      };

      const reviewPromise = streamAiReview(requestPayload, runId);
      reviewPromise.catch(() => undefined);

      const response = await fetch(apiUrl('/api/pull-requests/summary/basic'), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(requestPayload),
      });

      if (!response.ok) {
        const apiError = (await response.json()) as ApiError;
        throw new Error(apiError.message || '获取 PR 摘要失败。');
      }

      const basicSummary = (await response.json()) as PullRequestSummary;
      if (analysisRunId.current !== runId) {
        return;
      }
      setSummary(basicSummary);
      setIsWorkbenchOpen(true);
      setIsLoading(false);

      try {
        const aiReview = await reviewPromise;
        if (analysisRunId.current !== runId) {
          return;
        }
        setSummary((current) => current ? { ...current, aiReview } : current);
      } catch (caughtError) {
        if (analysisRunId.current !== runId) {
          return;
        }
        setReviewError(caughtError instanceof Error ? caughtError.message : 'AI Review 生成失败。');
      } finally {
        if (analysisRunId.current === runId) {
          setIsReviewLoading(false);
        }
      }
    } catch (caughtError) {
      setError(caughtError instanceof Error ? caughtError.message : '获取 PR 摘要失败。');
      setIsLoading(false);
      setIsReviewLoading(false);
    } finally {
      setIsLoading(false);
    }
  }

  async function streamAiReview(requestPayload: {
    prUrl: string;
    modelConfig: ReturnType<typeof buildModelConfigPayload>;
    githubToken?: string;
  }, runId: number) {
    const response = await fetch(apiUrl('/api/pull-requests/review/stream'), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      },
      body: JSON.stringify(requestPayload),
    });

    if (!response.ok) {
      const apiError = (await response.json()) as ApiError;
      throw new Error(apiError.message || 'AI Review 生成失败。');
    }
    if (!response.body) {
      throw new Error('浏览器未返回 AI Review 流。');
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let finalReview: AiReview | null = null;

    try {
      while (true) {
        const { value, done } = await reader.read();
        if (done) {
          break;
        }
        if (analysisRunId.current !== runId) {
          await reader.cancel();
          throw new Error('本次分析已取消。');
        }

        buffer += decoder.decode(value, { stream: true });
        const parsed = consumeStreamBuffer(buffer);
        buffer = parsed.remaining;
        for (const event of parsed.events) {
          if (event.type === 'chunk' && event.text) {
            setReviewStreamText((current) => current + event.text);
          }
          if (event.type === 'result' && event.review) {
            finalReview = event.review;
          }
          if (event.type === 'error') {
            throw new Error(event.message || 'AI Review 生成失败。');
          }
        }
      }

      buffer += decoder.decode();
      const parsed = consumeStreamBuffer(buffer, true);
      for (const event of parsed.events) {
        if (event.type === 'chunk' && event.text) {
          setReviewStreamText((current) => current + event.text);
        }
        if (event.type === 'result' && event.review) {
          finalReview = event.review;
        }
        if (event.type === 'error') {
          throw new Error(event.message || 'AI Review 生成失败。');
        }
      }
    } finally {
      reader.releaseLock();
    }

    if (!finalReview) {
      throw new Error('AI Review 流结束但没有返回结构化结果。');
    }
    return finalReview;
  }

  return (
    <main className="shell">
      <section className="workspace" aria-labelledby="page-title">
        <header className="masthead">
          <div>
            <p className="eyebrow">AI PR Review Assistant</p>
            <h1 id="page-title">PR Review 工作台</h1>
          </div>
          <button
            className="model-settings-trigger"
            type="button"
            aria-haspopup="dialog"
            aria-expanded={isModelPanelOpen}
            onClick={() => setIsModelPanelOpen(true)}
          >
            <span className={`model-ready-dot ${modelReady ? 'ready' : ''}`} aria-hidden="true" />
            <span>
              <strong>{selectedProvider?.displayName ?? '模型'}</strong>
              <small>{modelConfig.modelId || '未配置'}</small>
            </span>
            <SlidersHorizontal aria-hidden="true" size={18} />
          </button>
        </header>

        <section className="review-panel" aria-label="PR 分析入口">
          <form onSubmit={handleSubmit}>
            <label htmlFor="pr-url">GitHub PR URL</label>
            <div className="input-row">
              <input
                id="pr-url"
                type="url"
                placeholder={sampleUrl}
                value={prUrl}
                onChange={(event) => setPrUrl(event.target.value)}
              />
              <button type="submit" disabled={isLoading || isReviewLoading}>
                {isLoading ? (
                  <Loader2 className="spin" aria-hidden="true" size={18} />
                ) : (
                  <GitPullRequestArrow aria-hidden="true" size={18} />
                )}
                {isLoading ? '获取 PR' : isReviewLoading ? 'AI 分析中' : '分析 PR'}
              </button>
            </div>
          </form>
          <p className="hint">
            当前支持公开与私有 GitHub PR 的基础摘要、变更文件获取与 AI Review。私有仓库可在 GitHub 访问设置中提供 Token。
          </p>
          <div className="access-toolbar" aria-label="访问配置">
            <button
              className="secondary-button access-button"
              type="button"
              aria-haspopup="dialog"
              aria-expanded={isGitHubPanelOpen}
              onClick={() => setIsGitHubPanelOpen(true)}
            >
              <Github aria-hidden="true" size={17} />
              GitHub 访问
              <span className={`access-state ${githubToken.trim() ? 'ready' : ''}`}>
                {githubToken.trim() ? 'Token 已填写' : '公开模式'}
              </span>
            </button>
          </div>
          {error && (
            <div className="alert" role="alert">
              <AlertTriangle aria-hidden="true" size={18} />
              {error}
            </div>
          )}
        </section>

        {isModelPanelOpen && (
          <div className="model-dialog-backdrop" role="presentation" onMouseDown={() => setIsModelPanelOpen(false)}>
            <section
              className="model-dialog"
              role="dialog"
              aria-modal="true"
              aria-labelledby="model-dialog-title"
              onMouseDown={(event) => event.stopPropagation()}
            >
              <div className="model-dialog-header">
                <div>
                  <p className="eyebrow">Model switch</p>
                  <h2 id="model-dialog-title">模型设置</h2>
                </div>
                <button
                  className="icon-button"
                  type="button"
                  aria-label="关闭模型设置"
                  onClick={() => setIsModelPanelOpen(false)}
                >
                  <X aria-hidden="true" size={18} />
                </button>
              </div>

              <div className="model-current-line">
                <div className={`model-ready-dot ${modelReady ? 'ready' : ''}`} aria-hidden="true" />
                <span>{selectedProvider?.displayName ?? '自定义模型'}</span>
                <strong>{modelConfig.modelId || '未配置模型'}</strong>
                <em>{modelReady ? '就绪' : '需要 Key'}</em>
              </div>

              {modelConfigError && (
                <div className="model-warning" role="alert">
                  <AlertTriangle aria-hidden="true" size={17} />
                  {modelConfigError}
                </div>
              )}

              <div className="model-config-grid">
                <div className="provider-switch" role="group" aria-label="模型供应商">
                  {(modelOptions?.providers ?? []).map((provider) => (
                    <button
                      className={`provider-option ${provider.id === modelConfig.providerId ? 'active' : ''}`}
                      type="button"
                      key={provider.id}
                      onClick={() => selectProvider(provider)}
                    >
                      <Bot aria-hidden="true" size={17} />
                      <span>{provider.displayName}</span>
                      <small>{provider.apiKeyAvailable ? '环境 Key 已就绪' : provider.apiKeyEnv}</small>
                    </button>
                  ))}
                </div>

                <div className="model-fields">
                  <label htmlFor="model-base-url">Base URL</label>
                  <input
                    id="model-base-url"
                    type="url"
                    value={modelConfig.baseUrl}
                    onChange={(event) => updateModelConfig('baseUrl', event.target.value)}
                  />

                  <label htmlFor="model-id">Model ID</label>
                  <input
                    id="model-id"
                    value={modelConfig.modelId}
                    onChange={(event) => updateModelConfig('modelId', event.target.value)}
                  />

                  <label htmlFor="model-api-key">API Key</label>
                  <input
                    id="model-api-key"
                    type="password"
                    autoComplete="off"
                    placeholder={selectedProvider?.apiKeyAvailable ? '使用本机环境变量' : 'sk-...'}
                    value={modelConfig.apiKey}
                    onChange={(event) => updateModelConfig('apiKey', event.target.value)}
                  />
                </div>
              </div>

              <div className="model-dialog-actions">
                <button className="secondary-button" type="button" onClick={() => setIsModelPanelOpen(false)}>
                  完成
                </button>
              </div>
            </section>
          </div>
        )}

        {isGitHubPanelOpen && (
          <div className="model-dialog-backdrop" role="presentation" onMouseDown={() => setIsGitHubPanelOpen(false)}>
            <section
              className="model-dialog compact-dialog"
              role="dialog"
              aria-modal="true"
              aria-labelledby="github-dialog-title"
              onMouseDown={(event) => event.stopPropagation()}
            >
              <div className="model-dialog-header">
                <div>
                  <p className="eyebrow">GitHub access</p>
                  <h2 id="github-dialog-title">GitHub 访问设置</h2>
                </div>
                <button
                  className="icon-button"
                  type="button"
                  aria-label="关闭 GitHub 访问设置"
                  onClick={() => setIsGitHubPanelOpen(false)}
                >
                  <X aria-hidden="true" size={18} />
                </button>
              </div>

              <div className="token-note">
                <Github aria-hidden="true" size={20} />
                <div>
                  <strong>{githubToken.trim() ? 'Token 将用于本次分析请求' : '未填写 Token 时使用公开访问'}</strong>
                  <p>Token 只保存在当前页面内存中，不会写入仓库、日志或浏览器存储。</p>
                </div>
              </div>

              <div className="token-fields">
                <label htmlFor="github-token">GitHub Token</label>
                <input
                  id="github-token"
                  type="password"
                  autoComplete="off"
                  placeholder="github_pat_... 或 ghp_..."
                  value={githubToken}
                  onChange={(event) => setGithubToken(event.target.value)}
                />
              </div>

              <div className="model-dialog-actions">
                <button className="secondary-button" type="button" onClick={() => setGithubToken('')}>
                  清空
                </button>
                <button className="secondary-button" type="button" onClick={() => setIsGitHubPanelOpen(false)}>
                  完成
                </button>
              </div>
            </section>
          </div>
        )}

        {summary && !isWorkbenchOpen && (
          <section className="result-dock" aria-label="最近一次分析结果">
            <div>
              <p className="eyebrow">{summary.owner} / {summary.repository}</p>
              <h2>{summary.title}</h2>
            </div>
            <button type="button" onClick={() => setIsWorkbenchOpen(true)}>
              打开分析结果
              <ExternalLink aria-hidden="true" size={16} />
            </button>
          </section>
        )}

        {summary && isWorkbenchOpen && (
          <div className="workbench-backdrop" role="presentation" onMouseDown={() => setIsWorkbenchOpen(false)}>
            <section
              className="workbench-dialog"
              role="dialog"
              aria-modal="true"
              aria-labelledby="workbench-title"
              onMouseDown={(event) => event.stopPropagation()}
            >
              <div className="workbench-header">
                <div>
                  <p className="eyebrow">{summary.owner} / {summary.repository}</p>
                  <h2 id="workbench-title">{summary.title}</h2>
                </div>
                <div className="workbench-actions">
                  <a href={summary.htmlUrl} target="_blank" rel="noreferrer">
                    打开 PR
                    <ExternalLink aria-hidden="true" size={15} />
                  </a>
                  <button
                    className="icon-button"
                    type="button"
                    aria-label="关闭分析结果"
                    onClick={() => setIsWorkbenchOpen(false)}
                  >
                    <X aria-hidden="true" size={18} />
                  </button>
                </div>
              </div>

              <div className="workbench-tabs" role="tablist" aria-label="分析结果视图">
                <button
                  type="button"
                  role="tab"
                  aria-selected={workbenchView === 'pr-info'}
                  className={workbenchView === 'pr-info' ? 'active' : ''}
                  onClick={() => setWorkbenchView('pr-info')}
                >
                  <Files aria-hidden="true" size={17} />
                  PR 信息
                </button>
                <button
                  type="button"
                  role="tab"
                  aria-selected={workbenchView === 'ai-review'}
                  className={workbenchView === 'ai-review' ? 'active' : ''}
                  onClick={() => setWorkbenchView('ai-review')}
                >
                  {isReviewLoading ? (
                    <Loader2 className="spin" aria-hidden="true" size={17} />
                  ) : (
                    <Sparkles aria-hidden="true" size={17} />
                  )}
                  AI Review
                  <span className={`tab-state ${summary.aiReview?.generated ? 'ready' : reviewError ? 'error' : ''}`}>
                    {summary.aiReview?.generated ? '已完成' : reviewError ? '失败' : '生成中'}
                  </span>
                </button>
              </div>

              <div className="workbench-content">
                {workbenchView === 'pr-info' ? (
                  <PrInfoView
                    summary={summary}
                    changeSize={changeSize}
                    fileStats={fileStats}
                    contextCoverage={contextCoverage}
                    isReviewLoading={isReviewLoading}
                    reviewReady={Boolean(summary.aiReview?.generated)}
                    reviewError={reviewError}
                    onOpenAiReview={() => setWorkbenchView('ai-review')}
                  />
                ) : (
                  <AiReviewModules
                    summary={summary}
                    isReviewLoading={isReviewLoading}
                    reviewError={reviewError}
                    reviewStreamText={reviewStreamText}
                    reviewStats={reviewStats}
                    reviewMarkdown={reviewMarkdown}
                    markdownSourceLabel={markdownSourceLabel}
                    copyState={copyState}
                    onCopyMarkdown={copyMarkdown}
                  />
                )}
              </div>
            </section>
          </div>
        )}
      </section>
    </main>
  );

  async function copyMarkdown(markdown: string) {
    if (!markdown) {
      return;
    }

    try {
      await navigator.clipboard.writeText(markdown);
      setCopyState('copied');
      window.setTimeout(() => setCopyState('idle'), 1800);
    } catch {
      setCopyState('failed');
      window.setTimeout(() => setCopyState('idle'), 1800);
    }
  }

  function selectProvider(provider: ModelProvider) {
    setModelConfig((current) => ({
      ...current,
      providerId: provider.id,
      baseUrl: provider.baseUrl,
      modelId: provider.modelId,
      apiKey: '',
    }));
  }

  function updateModelConfig(field: keyof ModelFormState, value: string) {
    setModelConfig((current) => ({
      ...current,
      [field]: value,
    }));
  }
}

function SummaryMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}

function PrInfoView({
  summary,
  changeSize,
  fileStats,
  contextCoverage,
  isReviewLoading,
  reviewReady,
  reviewError,
  onOpenAiReview,
}: {
  summary: PullRequestSummary;
  changeSize: number | null;
  fileStats: {
    patchCount: number;
    largestFile: PullRequestFile | null;
    visibleTrackFiles: PullRequestFile[];
  } | null;
  contextCoverage: number;
  isReviewLoading: boolean;
  reviewReady: boolean;
  reviewError: string;
  onOpenAiReview: () => void;
}) {
  const fileExplanationMap = useMemo(() => {
    const map = new Map<string, string>();
    const explanations = summary.aiReview?.fileExplanations ?? [];
    for (const entry of explanations) {
      if (entry.filename && entry.explanation) {
        map.set(entry.filename, entry.explanation);
      }
    }
    return map;
  }, [summary.aiReview?.fileExplanations]);
  return (
    <div className="pr-form-view">
      <section className="summary-panel embedded" aria-label="PR 基础摘要">
        <div className="meta-grid">
          <SummaryMetric label="作者" value={summary.author} />
          <SummaryMetric label="状态" value={summary.draft ? 'Draft' : summary.state} />
          <SummaryMetric label="文件数" value={summary.changedFiles.toString()} />
          <SummaryMetric label="总变更" value={changeSize?.toString() ?? '0'} />
        </div>

        <div className="branch-line">
          <span>{summary.headBranch}</span>
          <ArrowRightLeft aria-hidden="true" size={16} />
          <span>{summary.baseBranch}</span>
        </div>

        <div className="diff-bar" aria-label="新增和删除统计">
          <span className="additions">+{summary.additions}</span>
          <span className="deletions">-{summary.deletions}</span>
        </div>
      </section>

      <section className="review-launch-card" aria-label="AI Review 准备状态">
        <div>
          <p className="eyebrow">AI Review</p>
          <h3>{reviewReady ? 'AI 分析已就绪' : reviewError ? 'AI 分析暂不可用' : 'AI 正在后台分析'}</h3>
          <p>
            {reviewReady
              ? '点击按钮查看模块化分析结果。'
              : reviewError || '你可以先核对 PR 信息，AI Review 结果生成后会自动填充。'}
          </p>
        </div>
        <button type="button" onClick={onOpenAiReview}>
          {isReviewLoading && !reviewReady && !reviewError ? (
            <Loader2 className="spin" aria-hidden="true" size={18} />
          ) : (
            <Sparkles aria-hidden="true" size={18} />
          )}
          {reviewReady ? '查看 AI Review' : '打开 AI Review'}
        </button>
      </section>

      <section className="files-panel embedded" aria-label="PR 变更文件">
        <div className="files-header">
          <div>
            <p className="eyebrow">Changed files</p>
            <h3>变更文件</h3>
          </div>
          <div className="file-count-badge">
            <Files aria-hidden="true" size={17} />
            {summary.files.length} 个文件
          </div>
        </div>

        <div className="file-flow" aria-hidden="true">
          <div className="flow-rail">
            {fileStats?.visibleTrackFiles.map((file, index) => (
              <span
                className={`flow-node ${statusTone(file.status)}`}
                key={`${file.filename}-${index}`}
                style={{ '--delay': `${index * 80}ms` } as CSSProperties}
                title={file.filename}
              />
            ))}
          </div>
          <div className="flow-summary">
            <span>{fileStats?.patchCount ?? 0} 个 patch 可用</span>
            <strong>{fileStats?.largestFile?.filename ?? '无文件变更'}</strong>
          </div>
        </div>

        <div className="file-list compact">
          {summary.files.map((file) => (
            <article className="file-row" key={file.filename}>
              <details className="file-details">
                <summary>
                  <div className="file-main">
                    <FileCode2 aria-hidden="true" size={18} />
                    <div>
                      <h3>{file.filename}</h3>
                      {file.previousFilename && (
                        <p>原文件：{file.previousFilename}</p>
                      )}
                    </div>
                  </div>
                  <div className="file-meta">
                    <span className={`status-tag ${statusTone(file.status)}`}>
                      {statusLabel(file.status)}
                    </span>
                    <span className="file-change additions">+{file.additions}</span>
                    <span className="file-change deletions">-{file.deletions}</span>
                    <span className="file-change">{file.changes} 行</span>
                    <span className={`patch-chip ${file.patch ? 'available' : ''}`}>
                      {file.patch ? '查看 patch' : '无 patch'}
                    </span>
                    {file.blobUrl && (
                      <a href={file.blobUrl} target="_blank" rel="noreferrer" aria-label={`打开 ${file.filename}`}>
                        <ExternalLink aria-hidden="true" size={15} />
                      </a>
                    )}
                  </div>
                  {fileExplanationMap.get(file.filename) && (
                    <p className="file-explanation">{fileExplanationMap.get(file.filename)}</p>
                  )}
                </summary>
                <div className="file-patch-panel">
                  {file.patch ? (
                    <pre>{formatPatch(file.patch)}</pre>
                  ) : (
                    <p>GitHub API 未返回该文件的 patch 内容，可能是二进制文件、文件过大或仅发生重命名。</p>
                  )}
                </div>
              </details>
            </article>
          ))}
        </div>
      </section>

      <section className="context-panel embedded" aria-label="Review 上下文">
        <div className="context-header">
          <div>
            <p className="eyebrow">Review context</p>
            <h3>模型上下文</h3>
          </div>
        </div>

        <div className="context-grid">
          <SummaryMetric label="上下文字符" value={summary.reviewContext.stats.promptCharacters.toString()} />
          <SummaryMetric label="Patch 覆盖" value={`${contextCoverage}%`} />
          <SummaryMetric label="可用 Patch" value={`${summary.reviewContext.stats.filesWithPatch}/${summary.reviewContext.stats.totalFiles}`} />
          <SummaryMetric label="截断文件" value={summary.reviewContext.stats.truncatedFiles.toString()} />
        </div>

        {summary.reviewContext.truncationNotes.length > 0 && (
          <div className="context-warnings">
            <strong>截断与缺失说明</strong>
            <ul>
              {summary.reviewContext.truncationNotes.map((note) => (
                <li key={note}>{note}</li>
              ))}
            </ul>
          </div>
        )}
      </section>
    </div>
  );
}

function AiReviewModules({
  summary,
  isReviewLoading,
  reviewError,
  reviewStreamText,
  reviewStats,
  reviewMarkdown,
  markdownSourceLabel,
  copyState,
  onCopyMarkdown,
}: {
  summary: PullRequestSummary;
  isReviewLoading: boolean;
  reviewError: string;
  reviewStreamText: string;
  reviewStats: {
    high: number;
    medium: number;
    low: number;
    needsHumanReview: number;
  };
  reviewMarkdown: string;
  markdownSourceLabel: string;
  copyState: 'idle' | 'copied' | 'failed';
  onCopyMarkdown: (markdown: string) => void;
}) {
  const review = summary.aiReview;
  const streamSnapshot = useMemo(() => buildStreamSnapshot(reviewStreamText), [reviewStreamText]);

  if (isReviewLoading && !review?.generated) {
    return (
      <div className="review-loading-panel" role="status">
        <div className="review-loading-title">
          <Loader2 className="spin" aria-hidden="true" size={22} />
          <div>
            <h3>AI Review 正在生成</h3>
            <p>PR 信息已经可查看，模型输出会在下方实时出现，完成后自动切换为模块化分析结果。</p>
          </div>
        </div>
        <div className="stream-preview structured-stream" aria-label="AI Review 生成进度">
          <div className="stream-stage-grid">
            {streamSnapshot.stages.map((stage) => (
              <div className={`stream-stage ${stage.active ? 'active' : ''}`} key={stage.label}>
                <span>{stage.label}</span>
                <strong>{stage.value}</strong>
              </div>
            ))}
          </div>
          <div className="stream-summary-preview">
            <strong>{streamSnapshot.heading}</strong>
            <p>{streamSnapshot.summary}</p>
          </div>
          {streamSnapshot.items.length > 0 && (
            <div className="stream-clue-list" aria-label="已识别的分析片段">
              {streamSnapshot.items.map((item) => (
                <span key={item}>{item}</span>
              ))}
            </div>
          )}
        </div>
      </div>
    );
  }

  if (reviewError) {
    return (
      <div className="ai-config-note review-error-panel" role="alert">
        <AlertTriangle aria-hidden="true" size={19} />
        <div>
          <strong>AI Review 生成失败</strong>
          <p>{reviewError}</p>
        </div>
      </div>
    );
  }

  if (!review) {
    return (
      <div className="empty-review">
        <AlertTriangle aria-hidden="true" size={19} />
        AI Review 尚未返回结果。
      </div>
    );
  }

  if (!review.generated) {
    return (
      <div className="ai-config-note" role="status">
        <AlertTriangle aria-hidden="true" size={19} />
        <div>
          <strong>AI Review 未生成</strong>
          <p>{review.message || '请配置模型 API Key 后重新分析。'}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="ai-module-view">
      <section className="review-module summary-module">
        <div className="module-title">
          <Sparkles aria-hidden="true" size={19} />
          <h3>变更总结</h3>
        </div>
        <p>{review.summary || 'AI Review 已生成，但没有返回摘要。'}</p>
        <div className="ai-model-line" aria-label="当前模型">
          <span>{review.providerId || 'default'}</span>
          <strong>{review.modelId || '未配置模型'}</strong>
        </div>
      </section>

      <section className="review-module">
        <div className="module-title">
          <ShieldAlert aria-hidden="true" size={19} />
          <h3>风险概览</h3>
        </div>
        <div className="risk-overview" aria-label="风险等级统计">
          <SummaryMetric label="高风险" value={reviewStats.high.toString()} />
          <SummaryMetric label="中风险" value={reviewStats.medium.toString()} />
          <SummaryMetric label="低风险" value={reviewStats.low.toString()} />
          <SummaryMetric label="需确认" value={reviewStats.needsHumanReview.toString()} />
        </div>
      </section>

      <section className="review-module">
        <div className="module-title">
          <FileCode2 aria-hidden="true" size={19} />
          <h3>风险代码识别</h3>
          <span>{review.riskItems.length} 项</span>
        </div>

        {review.riskItems.length > 0 ? (
          <div className="risk-list">
            {review.riskItems.map((item, index) => (
              <article className="risk-item" key={`${item.file}-${item.title}-${index}`}>
                <div className="risk-title-row">
                  <span className={`severity-tag ${severityTone(item.severity)}`}>
                    {severityLabel(item.severity)}
                  </span>
                  <span className={`confidence-tag ${confidenceTone(item.confidence)}`}>
                    {confidenceLabel(item.confidence)}
                  </span>
                  {item.needsHumanReview && (
                    <span className="human-review-tag">需人工确认</span>
                  )}
                  <h4>{item.title || '未命名风险'}</h4>
                </div>
                <p className="risk-file">{item.file || '未指定文件'}</p>
                <p>{item.detail || '模型未返回风险详情。'}</p>
                <div className="risk-evidence-grid">
                  <div>
                    <strong>判断依据</strong>
                    <span>{item.evidence || '模型未返回明确依据。'}</span>
                  </div>
                  <div>
                    <strong>可能影响</strong>
                    <span>{item.impact || '模型未返回影响说明。'}</span>
                  </div>
                </div>
                <div className="risk-recommendation">
                  <strong>建议</strong>
                  <span>{item.recommendation || '模型未返回修复建议。'}</span>
                </div>
              </article>
            ))}
          </div>
        ) : (
          <div className="empty-review">
            <CheckCircle2 aria-hidden="true" size={19} />
            未识别出明显风险。
          </div>
        )}
      </section>

      <section className="review-module">
        <div className="module-title">
          <Wrench aria-hidden="true" size={19} />
          <h3>Review 建议</h3>
        </div>
        <div className="review-action-grid">
          <ReviewActionGroup
            tone="required"
            title="必须修改"
            icon={<ShieldAlert aria-hidden="true" size={18} />}
            items={review.requiredActions ?? []}
            emptyText="暂无必须修改项。"
          />
          <ReviewActionGroup
            tone="suggested"
            title="建议优化"
            icon={<Wrench aria-hidden="true" size={18} />}
            items={review.suggestions}
            emptyText="暂无建议优化项。"
          />
          <ReviewActionGroup
            tone="follow-up"
            title="后续处理"
            icon={<ClipboardList aria-hidden="true" size={18} />}
            items={review.followUpItems ?? []}
            emptyText="暂无后续处理项。"
          />
        </div>
      </section>

      {(review.limitations?.length ?? 0) > 0 && (
        <section className="review-module limitation-module">
          <div className="module-title">
            <AlertTriangle aria-hidden="true" size={19} />
            <h3>判断限制</h3>
            <span>{review.limitations.length} 条</span>
          </div>
          <ul className="limitation-list">
            {review.limitations.map((limitation, index) => (
              <li key={`${limitation}-${index}`}>{limitation}</li>
            ))}
          </ul>
        </section>
      )}

      <section className="review-module markdown-box">
        <div className="markdown-header">
          <h3>Markdown</h3>
          <span className="markdown-source">{markdownSourceLabel}</span>
          <button
            className="copy-button"
            type="button"
            disabled={!reviewMarkdown}
            onClick={() => onCopyMarkdown(reviewMarkdown)}
          >
            {copyState === 'copied' ? (
              <CheckCircle2 aria-hidden="true" size={17} />
            ) : (
              <ClipboardCopy aria-hidden="true" size={17} />
            )}
            {copyState === 'copied' ? '已复制' : copyState === 'failed' ? '复制失败' : '复制 Markdown'}
          </button>
        </div>
        <pre>{reviewMarkdown || 'AI Review 暂无可复制 Markdown 内容。'}</pre>
      </section>
    </div>
  );
}

function ReviewActionGroup({
  tone,
  title,
  icon,
  items,
  emptyText,
}: {
  tone: 'required' | 'suggested' | 'follow-up';
  title: string;
  icon: ReactNode;
  items: string[];
  emptyText: string;
}) {
  return (
    <section className={`review-action-group ${tone}`} aria-label={title}>
      <div className="review-action-title">
        {icon}
        <strong>{title}</strong>
        <span>{items.length}</span>
      </div>
      {items.length > 0 ? (
        <ul>
          {items.map((item, index) => (
            <li key={`${title}-${item}-${index}`}>{item}</li>
          ))}
        </ul>
      ) : (
        <p>{emptyText}</p>
      )}
    </section>
  );
}

function statusLabel(status: string) {
  const labels: Record<string, string> = {
    added: '新增',
    modified: '修改',
    removed: '删除',
    renamed: '重命名',
    changed: '变更',
  };
  return labels[status] ?? status;
}

function statusTone(status: string) {
  const tones: Record<string, string> = {
    added: 'added',
    modified: 'modified',
    removed: 'removed',
    renamed: 'renamed',
  };
  return tones[status] ?? 'changed';
}

function severityLabel(severity: string) {
  const labels: Record<string, string> = {
    high: '高风险',
    medium: '中风险',
    low: '低风险',
  };
  return labels[severity] ?? (severity || '未知');
}

function severityTone(severity: string) {
  const tones: Record<string, string> = {
    high: 'high',
    medium: 'medium',
    low: 'low',
  };
  return tones[severity] ?? 'unknown';
}

function confidenceLabel(confidence: string) {
  const labels: Record<string, string> = {
    high: '高置信',
    medium: '中置信',
    low: '低置信',
  };
  return labels[confidence] ?? '置信度未知';
}

function confidenceTone(confidence: string) {
  const tones: Record<string, string> = {
    high: 'high',
    medium: 'medium',
    low: 'low',
  };
  return tones[confidence] ?? 'unknown';
}

function buildReviewMarkdown(summary: PullRequestSummary) {
  const review = summary.aiReview;
  if (!review) {
    return '';
  }

  const lines = [
    '## AI Review',
    '',
    `**PR**: ${summary.owner}/${summary.repository}#${summary.pullNumber}`,
    `**模型**: ${review.providerId || 'default'} / ${review.modelId || '未配置模型'}`,
    '',
    '### 变更总结',
    review.summary || 'AI Review 未返回摘要。',
    '',
    '### 风险代码识别',
  ];

  if (review.riskItems.length === 0) {
    lines.push('- 未识别出明确风险。');
  } else {
    review.riskItems.forEach((item, index) => {
      lines.push(
        `${index + 1}. **${severityLabel(item.severity)} / ${confidenceLabel(item.confidence)}** ${item.title || '未命名风险'}`,
        `   - 文件：\`${item.file || '未指定文件'}\``,
        `   - 详情：${item.detail || '模型未返回风险详情。'}`,
        `   - 依据：${item.evidence || '模型未返回明确依据。'}`,
        `   - 影响：${item.impact || '模型未返回影响说明。'}`,
        `   - 建议：${item.recommendation || '模型未返回修复建议。'}`
      );
      if (item.needsHumanReview) {
        lines.push('   - 标记：需要人工确认');
      }
    });
  }

  appendMarkdownGroup(lines, '### 必须修改', review.requiredActions, '暂无必须修改项。');
  appendMarkdownGroup(lines, '### 建议优化', review.suggestions, '暂无建议优化项。');
  appendMarkdownGroup(lines, '### 后续处理', review.followUpItems, '暂无后续处理项。');

  if ((review.limitations?.length ?? 0) > 0) {
    appendMarkdownGroup(lines, '### 判断限制', review.limitations, '');
  }

  return lines.join('\n').trim();
}

function appendMarkdownGroup(lines: string[], title: string, items: string[] = [], emptyText: string) {
  lines.push('', title);
  if (items.length === 0) {
    if (emptyText) {
      lines.push(`- ${emptyText}`);
    }
    return;
  }
  items.forEach((item) => lines.push(`- ${item}`));
}

function buildModelConfigPayload(modelConfig: ModelFormState) {
  return {
    providerId: modelConfig.providerId || undefined,
    baseUrl: modelConfig.baseUrl.trim() || undefined,
    modelId: modelConfig.modelId.trim() || undefined,
    apiKey: modelConfig.apiKey.trim() || undefined,
  };
}

function formatPatch(patch: string) {
  return patch.replace(/\r\n/g, '\n').trimEnd();
}

function buildStreamSnapshot(streamText: string) {
  const snapshot = parsePartialReviewPayload(streamText);
  const received = streamText.trim().length > 0;
  const riskCount = snapshot.riskItems.length;
  const suggestionCount = snapshot.suggestions.length + snapshot.requiredActions.length + snapshot.followUpItems.length;
  const limitationCount = snapshot.limitations.length;

  return {
    heading: received ? '正在整理结构化 Review' : '等待模型开始返回',
    summary: snapshot.summary || (received
      ? '模型内容正在返回，系统会在完整结果到达后切换为风险、建议和 Markdown 模块。'
      : '已向模型提交 PR 上下文，正在等待第一段分析结果。'),
    items: [
      ...snapshot.riskItems.map((item) => item.title || item.file || item.detail),
      ...snapshot.requiredActions,
      ...snapshot.suggestions,
      ...snapshot.followUpItems,
    ].filter(Boolean).slice(0, 5),
    stages: [
      { label: '模型响应', value: received ? '接收中' : '等待中', active: received },
      { label: '风险项', value: riskCount > 0 ? `${riskCount} 项` : '识别中', active: riskCount > 0 },
      { label: '建议', value: suggestionCount > 0 ? `${suggestionCount} 条` : '整理中', active: suggestionCount > 0 },
      { label: '限制说明', value: limitationCount > 0 ? `${limitationCount} 条` : '检查中', active: limitationCount > 0 },
    ],
  };
}

function parsePartialReviewPayload(streamText: string) {
  return {
    summary: findStringValue(streamText, 'summary'),
    riskItems: findObjectArrayItems(streamText, 'riskItems').map((item) => ({
      title: findStringValue(item, 'title'),
      file: findStringValue(item, 'file'),
      detail: findStringValue(item, 'detail'),
    })),
    requiredActions: findStringArrayItems(streamText, 'requiredActions'),
    suggestions: findStringArrayItems(streamText, 'suggestions'),
    followUpItems: findStringArrayItems(streamText, 'followUpItems'),
    limitations: findStringArrayItems(streamText, 'limitations'),
  };
}

function findStringValue(source: string, key: string) {
  const match = source.match(new RegExp(`"${key}"\\s*:\\s*"((?:\\\\.|[^"\\\\])*)"`));
  return match ? decodeJsonString(match[1]) : '';
}

function findStringArrayItems(source: string, key: string) {
  const body = findArrayBody(source, key);
  if (!body) {
    return [];
  }
  return [...body.matchAll(/"((?:\\.|[^"\\])*)"/g)]
    .map((match) => decodeJsonString(match[1]))
    .filter(Boolean)
    .slice(0, 8);
}

function findObjectArrayItems(source: string, key: string) {
  const body = findArrayBody(source, key);
  if (!body) {
    return [];
  }

  const items: string[] = [];
  let depth = 0;
  let start = -1;
  let inString = false;
  let escaping = false;
  for (let index = 0; index < body.length; index += 1) {
    const char = body[index];
    if (escaping) {
      escaping = false;
      continue;
    }
    if (char === '\\' && inString) {
      escaping = true;
      continue;
    }
    if (char === '"') {
      inString = !inString;
      continue;
    }
    if (inString) {
      continue;
    }
    if (char === '{') {
      if (depth === 0) {
        start = index;
      }
      depth += 1;
    }
    if (char === '}') {
      depth -= 1;
      if (depth === 0 && start !== -1) {
        items.push(body.slice(start, index + 1));
        start = -1;
      }
    }
  }
  return items.slice(0, 8);
}

function findArrayBody(source: string, key: string) {
  const keyMatch = source.match(new RegExp(`"${key}"\\s*:\\s*\\[`));
  if (!keyMatch || keyMatch.index === undefined) {
    return '';
  }
  let index = keyMatch.index + keyMatch[0].length;
  let depth = 1;
  let inString = false;
  let escaping = false;
  const start = index;
  for (; index < source.length; index += 1) {
    const char = source[index];
    if (escaping) {
      escaping = false;
      continue;
    }
    if (char === '\\' && inString) {
      escaping = true;
      continue;
    }
    if (char === '"') {
      inString = !inString;
      continue;
    }
    if (inString) {
      continue;
    }
    if (char === '[') {
      depth += 1;
    }
    if (char === ']') {
      depth -= 1;
      if (depth === 0) {
        return source.slice(start, index);
      }
    }
  }
  return source.slice(start);
}

function decodeJsonString(value: string) {
  try {
    return JSON.parse(`"${value}"`) as string;
  } catch {
    return value.replace(/\\"/g, '"').replace(/\\n/g, '\n');
  }
}

function consumeStreamBuffer(buffer: string, flush = false) {
  const normalized = buffer.replace(/\r\n/g, '\n');
  const boundary = normalized.lastIndexOf('\n\n');
  if (boundary === -1 && !flush) {
    return { events: [] as AiReviewStreamEvent[], remaining: buffer };
  }

  const consumable = flush ? normalized : normalized.slice(0, boundary);
  const remaining = flush ? '' : normalized.slice(boundary + 2);
  const events = consumable
    .split('\n\n')
    .map((eventBlock) => eventBlock
      .split('\n')
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).trimStart())
      .join('\n')
      .trim())
    .filter(Boolean)
    .map((data) => JSON.parse(data) as AiReviewStreamEvent);

  return { events, remaining };
}
