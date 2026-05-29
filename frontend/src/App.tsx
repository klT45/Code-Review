import { CSSProperties, FormEvent, ReactNode, useEffect, useMemo, useState } from 'react';
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
  ServerCog,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
  Wrench,
  X,
} from 'lucide-react';

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
  requiredActions: string[];
  suggestions: string[];
  followUpItems: string[];
  limitations: string[];
  markdown: string;
  message: string;
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
  const [copyState, setCopyState] = useState<'idle' | 'copied' | 'failed'>('idle');
  const [modelOptions, setModelOptions] = useState<ModelConfigurationOptions | null>(null);
  const [modelConfig, setModelConfig] = useState<ModelFormState>(emptyModelForm);
  const [modelConfigError, setModelConfigError] = useState('');
  const [isModelPanelOpen, setIsModelPanelOpen] = useState(false);
  const [githubToken, setGithubToken] = useState('');
  const [isGitHubPanelOpen, setIsGitHubPanelOpen] = useState(false);

  useEffect(() => {
    let isActive = true;

    async function loadModelOptions() {
      try {
        const response = await fetch('/api/model-config');
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
    if (!isModelPanelOpen && !isGitHubPanelOpen) {
      return undefined;
    }

    function closeOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setIsModelPanelOpen(false);
        setIsGitHubPanelOpen(false);
      }
    }

    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [isGitHubPanelOpen, isModelPanelOpen]);

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
    setSummary(null);
    setCopyState('idle');

    if (!prUrl.trim()) {
      setError('请输入 GitHub PR 链接。');
      return;
    }

    setIsLoading(true);
    try {
      const response = await fetch('/api/pull-requests/summary', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          prUrl: prUrl.trim(),
          modelConfig: buildModelConfigPayload(modelConfig),
          githubToken: githubToken.trim() || undefined,
        }),
      });

      if (!response.ok) {
        const apiError = (await response.json()) as ApiError;
        throw new Error(apiError.message || '获取 PR 摘要失败。');
      }

      setSummary((await response.json()) as PullRequestSummary);
    } catch (caughtError) {
      setError(caughtError instanceof Error ? caughtError.message : '获取 PR 摘要失败。');
    } finally {
      setIsLoading(false);
    }
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
              <button type="submit" disabled={isLoading}>
                {isLoading ? (
                  <Loader2 className="spin" aria-hidden="true" size={18} />
                ) : (
                  <GitPullRequestArrow aria-hidden="true" size={18} />
                )}
                {isLoading ? '分析中' : '分析 PR'}
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

        {summary && (
          <>
            <section className="summary-panel" aria-label="PR 基础摘要">
              <div className="summary-header">
                <div>
                  <p className="eyebrow">{summary.owner} / {summary.repository}</p>
                  <h2>{summary.title}</h2>
                </div>
                <a href={summary.htmlUrl} target="_blank" rel="noreferrer">
                  打开 PR
                  <ExternalLink aria-hidden="true" size={15} />
                </a>
              </div>

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

            {summary.aiReview && (
              <section className={`ai-panel ${summary.aiReview.generated ? 'generated' : 'not-ready'}`} aria-label="AI Review 结果">
                <div className="ai-header">
                  <div>
                    <p className="eyebrow">AI Review</p>
                    <h2>智能 Review</h2>
                  </div>
                  <div className="ai-state">
                    {summary.aiReview.generated ? (
                      <ShieldCheck aria-hidden="true" size={18} />
                    ) : (
                      <ShieldAlert aria-hidden="true" size={18} />
                    )}
                    {summary.aiReview.generated ? '已生成' : '等待配置'}
                  </div>
                </div>

                <div className="ai-model-line" aria-label="当前模型">
                  <span>{summary.aiReview.providerId || 'default'}</span>
                  <strong>{summary.aiReview.modelId || '未配置模型'}</strong>
                </div>

                {summary.aiReview.generated ? (
                  <>
                    <div className="ai-summary-block">
                      <Sparkles aria-hidden="true" size={20} />
                      <p>{summary.aiReview.summary || 'AI Review 已生成，但没有返回摘要。'}</p>
                    </div>

                    <div className="risk-overview" aria-label="风险等级统计">
                      <SummaryMetric label="高风险" value={reviewStats.high.toString()} />
                      <SummaryMetric label="中风险" value={reviewStats.medium.toString()} />
                      <SummaryMetric label="低风险" value={reviewStats.low.toString()} />
                      <SummaryMetric label="需确认" value={reviewStats.needsHumanReview.toString()} />
                    </div>

                    <div className="ai-section">
                      <div className="ai-section-header">
                        <h3>风险代码识别</h3>
                        <span>{summary.aiReview.riskItems.length} 项</span>
                      </div>

                      {summary.aiReview.riskItems.length > 0 ? (
                        <div className="risk-list">
                          {summary.aiReview.riskItems.map((item, index) => (
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
                    </div>

                    <div className="ai-section">
                      <div className="ai-section-header">
                        <h3>Review 建议</h3>
                        <span>
                          {(summary.aiReview.requiredActions?.length ?? 0)
                            + summary.aiReview.suggestions.length
                            + (summary.aiReview.followUpItems?.length ?? 0)} 条
                        </span>
                      </div>

                      {(summary.aiReview.requiredActions?.length ?? 0)
                        + summary.aiReview.suggestions.length
                        + (summary.aiReview.followUpItems?.length ?? 0) > 0 ? (
                        <div className="review-action-grid">
                          <ReviewActionGroup
                            tone="required"
                            title="必须修改"
                            icon={<ShieldAlert aria-hidden="true" size={18} />}
                            items={summary.aiReview.requiredActions ?? []}
                            emptyText="暂无必须修改项。"
                          />
                          <ReviewActionGroup
                            tone="suggested"
                            title="建议优化"
                            icon={<Wrench aria-hidden="true" size={18} />}
                            items={summary.aiReview.suggestions}
                            emptyText="暂无建议优化项。"
                          />
                          <ReviewActionGroup
                            tone="follow-up"
                            title="后续处理"
                            icon={<ClipboardList aria-hidden="true" size={18} />}
                            items={summary.aiReview.followUpItems ?? []}
                            emptyText="暂无后续处理项。"
                          />
                        </div>
                      ) : (
                        <div className="empty-review">
                          <CheckCircle2 aria-hidden="true" size={19} />
                          暂无额外建议。
                        </div>
                      )}
                    </div>

                    {(summary.aiReview.limitations?.length ?? 0) > 0 && (
                      <div className="ai-section">
                        <div className="ai-section-header">
                          <h3>判断限制</h3>
                          <span>{summary.aiReview.limitations.length} 条</span>
                        </div>
                        <ul className="limitation-list">
                          {summary.aiReview.limitations.map((limitation, index) => (
                            <li key={`${limitation}-${index}`}>{limitation}</li>
                          ))}
                        </ul>
                      </div>
                    )}

                    <div className="markdown-box">
                      <div className="markdown-header">
                        <h3>Markdown</h3>
                        <span className="markdown-source">{markdownSourceLabel}</span>
                        <button
                          className="copy-button"
                          type="button"
                          disabled={!reviewMarkdown}
                          onClick={() => copyMarkdown(reviewMarkdown)}
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
                    </div>
                  </>
                ) : (
                  <div className="ai-config-note" role="status">
                    <AlertTriangle aria-hidden="true" size={19} />
                    <div>
                      <strong>AI Review 未生成</strong>
                      <p>{summary.aiReview.message || '请配置模型 API Key 后重新分析。'}</p>
                    </div>
                  </div>
                )}
              </section>
            )}

            <section className="files-panel" aria-label="PR 变更文件">
              <div className="files-header">
                <div>
                  <p className="eyebrow">Changed files</p>
                  <h2>变更文件</h2>
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

              <div className="file-list">
                {summary.files.map((file) => (
                  <article className="file-row" key={file.filename}>
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
                        {file.patch ? 'patch 可用' : '无 patch'}
                      </span>
                      {file.blobUrl && (
                        <a href={file.blobUrl} target="_blank" rel="noreferrer" aria-label={`打开 ${file.filename}`}>
                          <ExternalLink aria-hidden="true" size={15} />
                        </a>
                      )}
                    </div>
                  </article>
                ))}
              </div>
            </section>

            <section className="context-panel" aria-label="Review 上下文">
              <div className="context-header">
                <div>
                  <p className="eyebrow">Review context</p>
                  <h2>模型上下文</h2>
                </div>
                <div className="context-meter" aria-label={`上下文长度 ${summary.reviewContext.stats.promptCharacters} 字符`}>
                  <span
                    style={{
                      width: `${Math.min(
                        100,
                        (summary.reviewContext.stats.promptCharacters
                          / summary.reviewContext.stats.maxPromptCharacters) * 100
                      )}%`,
                    }}
                  />
                </div>
              </div>

              <div className="context-grid">
                <SummaryMetric label="上下文字符" value={summary.reviewContext.stats.promptCharacters.toString()} />
                <SummaryMetric label="Patch 覆盖" value={`${contextCoverage}%`} />
                <SummaryMetric label="可用 Patch" value={`${summary.reviewContext.stats.filesWithPatch}/${summary.reviewContext.stats.totalFiles}`} />
                <SummaryMetric label="截断文件" value={summary.reviewContext.stats.truncatedFiles.toString()} />
              </div>

              <div className="context-note">
                <strong>上下文策略</strong>
                <p>
                  每个文件最多保留 {summary.reviewContext.stats.maxPatchCharactersPerFile} 个 patch 字符，
                  总 prompt 最多保留 {summary.reviewContext.stats.maxPromptCharacters} 个字符。后续 AI Review 将基于该结构化上下文生成总结、风险识别和建议。
                </p>
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
          </>
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
