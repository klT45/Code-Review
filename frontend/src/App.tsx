import { ClipboardCopy, GitPullRequestArrow, KeyRound, ServerCog } from 'lucide-react';

const upcomingCapabilities = [
  '公开与私有 GitHub PR 解析',
  'DeepSeek 默认模型接入',
  'OpenAI-compatible 多模型配置',
  '结构化 Review 报告与 Markdown 复制',
];

export function App() {
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
            基础框架已就绪
          </div>
        </header>

        <section className="review-panel" aria-label="PR 分析入口">
          <label htmlFor="pr-url">GitHub PR URL</label>
          <div className="input-row">
            <input
              id="pr-url"
              type="url"
              placeholder="https://github.com/owner/repo/pull/123"
              disabled
            />
            <button type="button" disabled>
              <GitPullRequestArrow aria-hidden="true" size={18} />
              分析 PR
            </button>
          </div>
          <p className="hint">后续 PR 将逐步接入 GitHub 变更获取、私有仓库 Token 和 AI Review。</p>
        </section>

        <section className="grid" aria-label="后续能力规划">
          <article className="card accent">
            <KeyRound aria-hidden="true" size={22} />
            <h2>模型配置</h2>
            <p>默认 DeepSeek，同时预留 API Key、Base URL、Model ID 的多模型切换方式。</p>
          </article>
          <article className="card">
            <ClipboardCopy aria-hidden="true" size={22} />
            <h2>Review 输出</h2>
            <p>结果会面向 GitHub Review 场景组织，并提供可复制 Markdown。</p>
          </article>
        </section>

        <section className="roadmap" aria-label="能力列表">
          {upcomingCapabilities.map((item) => (
            <div className="roadmap-item" key={item}>
              <span aria-hidden="true" />
              {item}
            </div>
          ))}
        </section>
      </section>
    </main>
  );
}
