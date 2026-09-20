(function(){
    const btn = document.getElementById('codes-refresh');
    const spinner = document.getElementById('codes-spinner');
    const tbody = document.querySelector('table.table-portal tbody');
    function setLoading(on){ if(spinner) spinner.style.display = on ? '' : 'none'; if(btn) btn.disabled = on; }
    function escapeHtml(s){ if(s==null) return ''; return String(s).replace(/[&<>"']/g, function(m){ return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]; }); }
    async function refresh(){ setLoading(true); try{ const r = await fetch('/api/tax/codes',{cache:'no-store'}); if(r.ok){ const codes = await r.json(); if(tbody){ tbody.innerHTML = codes.map(c=>`<tr><td>${escapeHtml(c.code)}</td><td>${escapeHtml(c.name)}</td><td>${escapeHtml(c.agency? c.agency.code : '')}</td><td class='num'>${c.rate}</td><td>${c.payableAccount? escapeHtml(c.payableAccount.accountNumber+' – '+c.payableAccount.accountName):'—'}</td><td>${c.active?'<span class="badge-pill paid">Active</span>':'<span class="badge-pill overdue">Inactive</span>'}</td></tr>`).join(''); } } }catch(e){ console.error('codes refresh failed', e);} finally{ setLoading(false);} }
    if(btn) btn.addEventListener('click', refresh);
})();