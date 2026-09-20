(function(){
    const btn = document.getElementById('agencies-refresh');
    const spinner = document.getElementById('agencies-spinner');
    const tbody = document.querySelector('table.table-portal tbody');
    function setLoading(on){ if(spinner) spinner.style.display = on ? '' : 'none'; if(btn) btn.disabled = on; }
    function escapeHtml(s){ if(s==null) return ''; return String(s).replace(/[&<>"']/g, function(m){ return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]; }); }
    async function refresh(){ setLoading(true); try{ const r = await fetch('/api/tax-agencies',{cache:'no-store'}); if(r.ok){ const js = await r.json(); if(tbody){ tbody.innerHTML = js.map(a=>`<tr><td>${escapeHtml(a.code)}</td><td>${escapeHtml(a.name)}</td><td>${escapeHtml(a.accountNumber||'')}</td><td>${a.website?`<a href="${escapeHtml(a.website)}" target="_blank">${escapeHtml(a.website)}</a>`:''}</td><td><a class='btn-portal btn-ghost btn-sm' href='/tax/agencies/${a.id}/report'><i class='bi bi-file-earmark-text'></i> Report</a></td></tr>`).join(''); } } }catch(e){ console.error('agencies refresh failed', e); } finally{ setLoading(false);} }
    if(btn) btn.addEventListener('click', refresh);
})();