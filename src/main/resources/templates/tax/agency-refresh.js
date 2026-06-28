(function(){
    const btn = document.getElementById('agency-refresh');
    const spinner = document.getElementById('agency-spinner');
    const container = document.getElementById('agency-report');
    function setLoading(on){ if(spinner) spinner.style.display = on ? '' : 'none'; if(btn) btn.disabled = on; }
    function formatCurrency(v){ try{ const n = Number(v); return new Intl.NumberFormat(undefined, {style:'currency', currency:'CAD'}).format(isNaN(n)?0:n); }catch(e){return v;} }
    async function refresh(){
        if(!container) return; const id = container.getAttribute('data-agency-id'); if(!id) return;
        setLoading(true);
        try{
            const r = await fetch(`/api/tax-agencies/${id}/report`, {cache:'no-store'});
            if(r.ok){ const j = await r.json(); document.querySelector('#agency-report .stat-value[th\\:text="${#numbers.formatCurrency(totalCollected)}"]')?.textContent = formatCurrency(j.totalCollected || 0);
                // update totals by selecting the numeric stat-value positions
                const vals = document.querySelectorAll('#agency-report .stat-value');
                // fallback: assign by order if present
                if(vals && vals.length>=3){ vals[0].textContent = formatCurrency(j.totalCollected || 0); vals[1].textContent = formatCurrency(j.totalItc || 0); vals[2].textContent = formatCurrency(j.totalNet || 0); }
                const tbody = document.querySelector('#agency-report table tbody'); if(tbody && Array.isArray(j.periods)){ tbody.innerHTML = j.periods.map(p=>`<tr><td><a href='/tax/periods/${p.id}'>${p.periodStart} → ${p.periodEnd}</a></td><td><span class='badge-pill'>${p.status}</span></td><td class='num'>${formatCurrency(p.taxCollected||0)}</td><td class='num'>${formatCurrency(p.taxITC||0)}</td><td class='num'>${formatCurrency(p.netOwing||0)}</td><td>${p.filedDate||'—'}</td><td>${p.paidDate||'—'}</td></tr>`).join(''); }
        }catch(e){ console.error('Agency refresh failed', e); }
        finally{ setLoading(false); }
    }
    if(btn) btn.addEventListener('click', refresh);
    setInterval(refresh,30000);
})();