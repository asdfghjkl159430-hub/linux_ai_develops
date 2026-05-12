(function () {
  const API = "/api";

  function toast(message, ok) {
    const area = document.getElementById("toast-area");
    const el = document.createElement("div");
    el.className = "toast " + (ok ? "ok" : "err");
    el.textContent = message;
    area.appendChild(el);
    setTimeout(() => el.remove(), 5500);
  }

  async function req(path, options = {}) {
    const opts = { ...options };
    const needJson =
      opts.body != null &&
      !(opts.body instanceof FormData) &&
      typeof opts.body === "object";
    opts.headers = opts.headers || {};
    if (needJson) {
      opts.headers["Content-Type"] = "application/json;charset=UTF-8";
      opts.body = JSON.stringify(opts.body);
    } else if (opts.body == null) {
      delete opts.body;
    }
    const res = await fetch(API + path, opts);
    const text = await res.text();
    let json;
    try {
      json = text ? JSON.parse(text) : null;
    } catch {
      throw new Error(res.status + ": " + (text || res.statusText));
    }
    if (!json || json.code !== 200) {
      const msg =
        json && json.message ? json.message : json ? String(json.code) : "请求失败";
      throw new Error(msg);
    }
    return json.data;
  }

  const statusLabels = {
    0: "排队",
    1: "运行中",
    2: "成功",
    3: "失败",
    4: "已取消",
  };

  async function refreshHealth() {
    const el = document.getElementById("health-status");
    try {
      const d = await req("/system/health");
      el.textContent = "UP";
      el.className = "badge up";
    } catch (e) {
      el.textContent = "不可用";
      el.className = "badge";
      el.title = e.message || "";
    }
  }

  let agentsCache = [];

  async function refreshAgents() {
    try {
      agentsCache = await req("/agents");
      const sel = document.getElementById("task-agent-type");
      const cur = sel.value;
      sel.innerHTML =
        '<option value="auto">自动选择 (async)</option>' +
        agentsCache
          .filter((a) => a.agentType !== "multi_agent")
          .map(
            (a) =>
              `<option value="${escapeHtml(a.agentType)}">${escapeHtml(
                a.name
              )} (${escapeHtml(a.agentType)})</option>`
          )
          .join("");
      if (cur && [...sel.options].some((o) => o.value === cur)) sel.value = cur;
    } catch (e) {
      console.warn(e);
      agentsCache = [];
    }
  }

  function escapeHtml(s) {
    if (s == null) return "";
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/"/g, "&quot;");
  }

  async function refreshServers() {
    const tbody = document.getElementById("servers-body");
    tbody.innerHTML = '<tr><td colspan="6" class="loading">加载中…</td></tr>';
    try {
      const list = await req("/servers");
      const selects = ["task-server-id", "wf-server-id"];
      selects.forEach((id) => {
        const s = document.getElementById(id);
        const cur = s.value;
        s.innerHTML =
          '<option value="">请选择</option>' +
          list
            .map((sv) =>
              sv.status === 1
                ? `<option value="${sv.id}">#${sv.id} ${escapeHtml(sv.name)} (${escapeHtml(
                    sv.host
                  )}:${sv.port ?? 22})</option>`
                : ""
            )
            .join("");
        if (cur && [...s.options].some((o) => o.value === cur)) s.value = cur;
      });
      tbody.innerHTML = "";
      if (!list.length) {
        tbody.innerHTML =
          '<tr><td colspan="6" class="loading">暂无服务器，请先添加。</td></tr>';
        return;
      }
      for (const s of list) {
        const tr = document.createElement("tr");
        const statusText = s.status === 1 ? "启用" : "停用";
        tr.innerHTML = `
          <td>${s.id}</td>
          <td>${escapeHtml(s.name)}</td>
          <td class="mono">${escapeHtml(s.host)}:${s.port ?? 22}</td>
          <td>${escapeHtml(s.username)}</td>
          <td>••••</td>
          <td>
            ${statusText}
            <button type="button" class="secondary small srv-test" data-id="${s.id}">SSH 测试</button>
            <button type="button" class="secondary small srv-diag" data-id="${s.id}">快速诊断</button>
            <button type="button" class="danger small srv-del" data-id="${s.id}">删除</button>
          </td>`;
        tbody.appendChild(tr);
      }
      tbody.querySelectorAll(".srv-test").forEach((btn) => {
        btn.addEventListener("click", () =>
          testServer(Number(btn.dataset.id))
        );
      });
      tbody.querySelectorAll(".srv-diag").forEach((btn) => {
        btn.addEventListener("click", () =>
          runDiag(Number(btn.dataset.id))
        );
      });
      tbody.querySelectorAll(".srv-del").forEach((btn) => {
        btn.addEventListener("click", () =>
          deleteServer(Number(btn.dataset.id))
        );
      });
    } catch (e) {
      tbody.innerHTML = `<tr><td colspan="6" class="loading">加载失败: ${escapeHtml(e.message)}</td></tr>`;
    }
  }

  async function testServer(id) {
    try {
      const ok = await req(`/servers/${id}/test`, { method: "POST" });
      toast(ok ? "SSH 连通成功" : "SSH 连通失败（请检查账号与 sshd）", !!ok);
    } catch (e) {
      toast(e.message, false);
    }
  }

  async function deleteServer(id) {
    if (!confirm("确认删除 #" + id + "？")) return;
    try {
      await req(`/servers/${id}`, { method: "DELETE" });
      toast("已删除", true);
      await refreshServers();
    } catch (e) {
      toast(e.message, false);
    }
  }

  async function runDiag(id) {
    try {
      toast("正在执行诊断…", true);
      const task = await req(`/servers/${id}/diagnostics`, {
        method: "POST",
        body: {},
      });
      toast("诊断任务 #" + task.id + " 已完成", true);
      await refreshTasks();
    } catch (e) {
      toast(e.message, false);
    }
  }

  async function refreshTasks() {
    const tbody = document.getElementById("tasks-body");
    tbody.innerHTML = '<tr><td colspan="6" class="loading">加载中…</td></tr>';
    try {
      const page = await req("/tasks?page=1&size=15");
      const list = page.records || [];
      tbody.innerHTML = "";
      if (!list.length) {
        tbody.innerHTML = '<tr><td colspan="6" class="loading">暂无任务</td></tr>';
        return;
      }
      for (const t of list) {
        const tr = document.createElement("tr");
        tr.className = "clickable";
        tr.dataset.id = t.id;
        const stLabel = statusLabels[t.status] ?? t.status;
        tr.innerHTML = `
          <td>${t.id}</td>
          <td class="mono">${escapeHtml(
            (t.userInput || "").slice(0, 80) +
              ((t.userInput || "").length > 80 ? "…" : "")
          )}</td>
          <td>${t.serverId ?? "-"}</td>
          <td>${escapeHtml(t.agentType || "")}</td>
          <td><span class="status-chip status-${t.status ?? 0}">${escapeHtml(stLabel)}</span></td>
          <td class="muted mono" style="color:var(--muted)">${t.createdAt || ""}</td>`;
        tr.addEventListener("click", () => openTaskDetail(t.id));
        tbody.appendChild(tr);
      }
    } catch (e) {
      tbody.innerHTML = `<tr><td colspan="6" class="loading">加载失败: ${escapeHtml(e.message)}</td></tr>`;
    }
  }

  async function openTaskDetail(taskId) {
    const modal = document.getElementById("task-modal");
    const inner = document.getElementById("task-modal-body");
    inner.innerHTML =
      '<p class="loading">加载详情…（含执行日志）</p>';
    modal.classList.add("open");
    try {
      const detail = await req(`/tasks/${taskId}`);
      const t = detail.task;
      let html = `
        <h3>任务 #${t.id}</h3>
        <p><strong>输入</strong><br/><span class="mono">${escapeHtml(t.userInput || "")}</span></p>
        <p><strong>Agent</strong> ${escapeHtml(t.agentType)} · <strong>服务器</strong> ${t.serverId} · 
        <span class="status-chip status-${t.status}">${escapeHtml(statusLabels[t.status] || t.status)}</span></p>
        <p class="mono" style="color:var(--muted)">${escapeHtml(t.errorMessage || t.resultSummary || "").slice(
          0,
          1200
        )}</p>
        <h4 style="margin:1rem 0 0.5rem">日志</h4>`;
      const logs = detail.logs || [];
      if (!logs.length) html += "<p class=\"muted\">无步骤日志。</p>";
      for (const log of logs) {
        html += `<div class="log-entry">
          <div><strong>步骤 ${log.step}</strong> — exit ${log.exitCode}</div>
          <div class="mono">${escapeHtml(log.command || "")}</div>
          <div class="mono" style="color:var(--muted);margin-top:0.35rem">${escapeHtml(
            (log.output || "").slice(0, 4000)
          )}</div>
        </div>`;
      }
      inner.innerHTML = html;
    } catch (e) {
      inner.innerHTML =
        `<p class="toast err">${escapeHtml(e.message)}</p>`;
    }
  }

  function closeModal() {
    document.getElementById("task-modal").classList.remove("open");
  }

  document.getElementById("btn-close-modal").addEventListener("click", closeModal);
  document.getElementById("task-modal").addEventListener("click", (ev) => {
    if (ev.target.id === "task-modal") closeModal();
  });

  document.getElementById("form-server").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const name = document.getElementById("srv-name").value.trim();
    const host = document.getElementById("srv-host").value.trim();
    const port = Number(document.getElementById("srv-port").value) || 22;
    const username = document.getElementById("srv-user").value.trim();
    const password = document.getElementById("srv-pass").value;
    try {
      await req("/servers", {
        method: "POST",
        body: {
          name,
          host,
          port,
          username,
          password,
          osType: "Linux",
        },
      });
      toast("服务器已添加", true);
      ev.target.reset();
      document.getElementById("srv-port").value = "22";
      await refreshServers();
    } catch (e) {
      toast(e.message, false);
    }
  });

  async function submitTask(asyncMode) {
    const userInput = document.getElementById("task-input").value.trim();
    const serverId = document.getElementById("task-server-id").value;
    const agentType = document.getElementById("task-agent-type").value;
    if (!userInput || !serverId) {
      toast("请填写任务说明并选择服务器", false);
      return;
    }
    const body = {
      userInput,
      serverId: Number(serverId),
      agentType: asyncMode ? agentType || "auto" : agentType || "command_executor",
    };
    try {
      if (asyncMode) {
        const task = await req("/tasks/async", { method: "POST", body });
        toast("异步任务已提交 #" + task.id, true);
      } else {
        toast("同步执行中，请稍候…", true);
        const task = await req("/tasks", { method: "POST", body });
        toast("同步任务完成 #" + task.id + " 状态码 " + task.status, task.status === 2);
      }
      document.getElementById("task-input").value = "";
      await refreshTasks();
    } catch (e) {
      toast(e.message, false);
    }
  }

  document.getElementById("btn-task-sync").addEventListener("click", () => submitTask(false));
  document.getElementById("btn-task-async").addEventListener("click", () => submitTask(true));

  document.getElementById("btn-workflow").addEventListener("click", async () => {
    const userInput = document.getElementById("wf-input").value.trim();
    const serverId = document.getElementById("wf-server-id").value;
    if (!userInput || !serverId) {
      toast("请填写工作流描述并选择服务器", false);
      return;
    }
    try {
      toast("提交多 Agent 工作流…", true);
      const task = await req("/tasks/workflow", {
        method: "POST",
        body: { userInput, serverId: Number(serverId) },
      });
      toast("任务 #" + task.id + " 已提交（异步编排）", true);
      await refreshTasks();
    } catch (e) {
      toast(e.message, false);
    }
  });

  document.getElementById("btn-refresh").addEventListener("click", async () => {
    await Promise.all([
      refreshHealth(),
      refreshServers(),
      refreshAgents(),
      refreshTasks(),
    ]);
    toast("列表已刷新", true);
  });

  (async function init() {
    await refreshHealth();
    await Promise.all([refreshServers(), refreshAgents(), refreshTasks()]);
  })();
})();
