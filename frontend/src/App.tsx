import { CSSProperties, FormEvent, useEffect, useMemo, useState } from 'react';
import {
  AlertTriangle,
  ArrowRightLeft,
  Bot,
  CheckCircle2,
  ClipboardCopy,
  ExternalLink,
  FileCode2,
  Files,
  GitPullRequestArrow,
  Loader2,
  SlidersHorizontal,
  ServerCog,
  ShieldAlert,
  ShieldCheck,
  Sparkles,
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
  recommendation: string;
};

type AiReview = {
  enabled: boolean;
  generated: boolean;
  providerId: string;
  modelId: string;
  summary: string;
  riskItems: AiRiskItem[];
  suggestions: string[];
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
    };
  }, [summary]);

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
          <div className="status-pill">
            <ServerCog aria-hidden="true" size={18} />
            AI 模型可切换
          </div>
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
            当前支持公开 GitHub PR 的基础摘要、变更文件获取与 AI Review。后续会继续接入私有仓库 Token。
          </p>
          {error && (
            <div className="alert" role="alert">
              <AlertTriangle aria-hidden="true" size={18} />
              {error}
            </div>
          )}
        </section>

        <section className={`model-panel ${isModelPanelOpen ? 'expanded' : ''}`} aria-label="AI 模型配置">
          <div className="model-panel-header">
            <div>
              <p className="eyebrow">Model switch</p>
              <h2>模型切换</h2>
            </div>
            <button
              className="secondary-button"
              type="button"
              aria-expanded={isModelPanelOpen}
              onClick={() => setIsModelPanelOpen((open) => !open)}
            >
              <SlidersHorizontal aria-hidden="true" size={17} />
              {isModelPanelOpen ? '收起配置' : '调整模型'}
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

          {isModelPanelOpen && (
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
          )}
        </section>

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
                                <h4>{item.title || '未命名风险'}</h4>
                              </div>
                              <p className="risk-file">{item.file || '未指定文件'}</p>
                              <p>{item.detail || '模型未返回风险详情。'}</p>
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
                        <span>{summary.aiReview.suggestions.length} 条</span>
                      </div>
                      {summary.aiReview.suggestions.length > 0 ? (
                        <ul className="suggestion-list">
                          {summary.aiReview.suggestions.map((suggestion, index) => (
                            <li key={`${suggestion}-${index}`}>{suggestion}</li>
                          ))}
                        </ul>
                      ) : (
                        <div className="empty-review">
                          <CheckCircle2 aria-hidden="true" size={19} />
                          暂无额外建议。
                        </div>
                      )}
                    </div>

                    <div className="markdown-box">
                      <div className="markdown-header">
                        <h3>Markdown</h3>
                        <button
                          className="copy-button"
                          type="button"
                          disabled={!summary.aiReview.markdown}
                          onClick={() => copyMarkdown(summary.aiReview?.markdown ?? '')}
                        >
                          {copyState === 'copied' ? (
                            <CheckCircle2 aria-hidden="true" size={17} />
                          ) : (
                            <ClipboardCopy aria-hidden="true" size={17} />
                          )}
                          {copyState === 'copied' ? '已复制' : copyState === 'failed' ? '复制失败' : '复制 Markdown'}
                        </button>
                      </div>
                      <pre>{summary.aiReview.markdown || 'AI Review 未返回 Markdown 内容。'}</pre>
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

function buildModelConfigPayload(modelConfig: ModelFormState) {
  return {
    providerId: modelConfig.providerId || undefined,
    baseUrl: modelConfig.baseUrl.trim() || undefined,
    modelId: modelConfig.modelId.trim() || undefined,
    apiKey: modelConfig.apiKey.trim() || undefined,
  };
}
