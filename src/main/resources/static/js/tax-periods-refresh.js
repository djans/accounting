(function(){
    const btn = document.getElementById('periods-refresh');
    const spinner = document.getElementById('periods-spinner');
    const tbody = document.querySelector('table.table-portal tbody');
    function setLoading(on){ if(spinner) spinner.style.display = on ? '' : 'none'; if(btn) btn.disabled = on; }
    function escapeHtml(s){ if(s==null) return ''; return String(s).replace(/[&<>"']/g, function(m){ return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]; }); }
    async function refresh(){ 
        setLoading(true); 
        try{ 
            const r = await fetch('/api/tax/periods',{cache:'no-store'}); 
            if(r.ok){ 
                const periods = await r.json(); 
                if(tbody){ 
                    tbody.innerHTML = periods.map(p=>`<tr><td>${escapeHtml(p.agency.code)}</td><td>${escapeHtml(p.periodStart)}</td><td><a href='/tax/periods/${p.id}'>${escapeHtml(p.periodEnd)}</a></td><td><span class='badge-pill'>${escapeHtml(p.status)}</span></td><td class='num'>${p.taxCollected? p.taxCollected : '$0'}</td><td class='num'>${p.taxItc? p.taxItc : '$0'}</td><td class='num'>${p.netOwing? p.netOwing : '$0'}</td><td>${p.filedDate? p.filedDate : '—'}</td><td>${p.paidDate? p.paidDate : '—'}</td></tr>`).join(''); 
                } 
            } 
        }catch(e){ console.error('periods refresh failed', e); } finally{ setLoading(false);} 
    }
    if(btn) btn.addEventListener('click', refresh);
})();