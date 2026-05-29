import { CSSProperties, FormEvent, useMemo, useState } from 'react';
import {
  AlertTriangle,
  ArrowRightLeft,
  ExternalLink,
  FileCode2,
  Files,
  GitPullRequestArrow,
  Loader2,
  ServerCog,
} from 'lucide-react';

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
};

type ApiError = {
  code: string;
  message: string;
  timestamp: string;
};

const sampleUrl = 'https://github.com/klT45/Code-Review/pull/2';
const maxTrackItems = 12;

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
            已接入变更文件
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
            当前支持公开 GitHub PR 的基础摘要与变更文件获取。后续会继续接入私有仓库 Token 和 AI Review。
          </p>
          {error && (
            <div className="alert" role="alert">
              <AlertTriangle aria-hidden="true" size={18} />
              {error}
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
          </>
        )}
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
