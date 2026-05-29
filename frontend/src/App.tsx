import { FormEvent, useMemo, useState } from 'react';
import {
  AlertTriangle,
  ArrowRightLeft,
  GitPullRequestArrow,
  Loader2,
  ServerCog,
} from 'lucide-react';

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
};

type ApiError = {
  code: string;
  message: string;
  timestamp: string;
};

const sampleUrl = 'https://github.com/klT45/Code-Review/pull/2';

export function App() {
  const [prUrl, setPrUrl] = useState('');
  const [summary, setSummary] = useState<PullRequestSummary | null>(null);
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);

  const changeSize = useMemo(() => {
    if (!summary) {
      return null;
    }
    return summary.additions + summary.deletions;
  }, [summary]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError('');
    setSummary(null);

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
        body: JSON.stringify({ prUrl: prUrl.trim() }),
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
            已接入基础摘要
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
                {isLoading ? '获取中' : '获取摘要'}
              </button>
            </div>
          </form>
          <p className="hint">
            当前支持公开 GitHub PR 的基础信息获取。后续会继续接入文件变更、私有仓库 Token 和 AI Review。
          </p>
          {error && (
            <div className="alert" role="alert">
              <AlertTriangle aria-hidden="true" size={18} />
              {error}
            </div>
          )}
        </section>

        {summary && (
          <section className="summary-panel" aria-label="PR 基础摘要">
            <div className="summary-header">
              <div>
                <p className="eyebrow">{summary.owner} / {summary.repository}</p>
                <h2>{summary.title}</h2>
              </div>
              <a href={summary.htmlUrl} target="_blank" rel="noreferrer">
                打开 PR
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
        )}

        <section className="grid" aria-label="后续能力规划">
          <article className="card accent">
            <h2>下一步：文件变更</h2>
            <p>获取 changed files 和 patch，为风险识别与 Review 建议提供上下文。</p>
          </article>
          <article className="card">
            <h2>后续：模型切换</h2>
            <p>默认 DeepSeek，支持 API Key、Base URL、Model ID 的多模型配置方式。</p>
          </article>
        </section>
      </section>
    </main>
  );
}

function SummaryMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="metric">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  );
}
