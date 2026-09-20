(function(){
    const btn = document.getElementById('recon-refresh');
    const spinner = document.getElementById('recon-spinner');
    const tbody = document.getElementById('recon-tbody');
    function setLoading(on){ if(spinner) spinner.style.display = on ? '' : 'none'; if(btn) btn.disabled = on; }
    function escapeHtml(s){ if(s==null) return ''; return String(s).replace(/[&<>"']/g, function(m){ return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]; }); }
    async function refresh(){ setLoading(true); try{ const r = await fetch('/api/tax/reconciliation',{cache:'no-store'}); if(r.ok){ const rows = await r.json(); if(tbody){ tbody.innerHTML = rows.map(row=>`<tr><td>${escapeHtml(row.agency.code+' — '+row.agency.name)}</td><td>${escapeHtml(row.code.code+' ('+row.code.name+')')}</td><td>${escapeHtml(row.account.accountNumber+' – '+row.account.accountName)}</td><td class='num'>${row.glBalance}</td></tr>`).join(''); } } }catch(e){ console.error('recon refresh failed', e);} finally{ setLoading(false);} }
    if(btn) btn.addEventListener('click', refresh);
})();