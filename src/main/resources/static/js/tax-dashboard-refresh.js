(function(){
    const btn = document.getElementById('tax-dashboard-refresh');
    const spinner = document.getElementById('tax-dashboard-spinner');
    const agenciesCount = document.getElementById('agencies-count');
    const codesCount = document.getElementById('codes-count');
    const openCount = document.getElementById('open-count');
    const filedCount = document.getElementById('filed-count');
    const periodsTbody = document.querySelector('.portal-card table.table-portal tbody');
    function setLoading(on){ if(spinner) spinner.style.display = on ? '' : 'none'; if(btn) btn.disabled = on; }
    function escapeHtml(s){ if(s==null) return ''; return String(s).replace(/[&<>"']/g, function(m){ return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]; }); }
    async function refresh(){ setLoading(true);
        try{
            const [agR, codesR, periodsR] = await Promise.all([
                fetch('/api/tax/agencies',{cache:'no-store'}),
                fetch('/api/tax/codes',{cache:'no-store'}),
                fetch('/api/tax/periods',{cache:'no-store'})
            ]);
            if(agR.ok){ const ags = await agR.json(); if(agenciesCount) agenciesCount.textContent = ags.length; }
            if(codesR.ok){ const cds = await codesR.json(); if(codesCount) codesCount.textContent = cds.length; }
            if(periodsR.ok){ 
                const periods = await periodsR.json(); 
                if(openCount) openCount.textContent = periods.filter(p=>p.status==='OPEN').length; 
                if(filedCount) filedCount.textContent = periods.filter(p=>p.status==='FILED' && !p.paid).length; 
                if(periodsTbody){ 
                    periodsTbody.innerHTML = periods.slice(0,10).map(p=>`<tr><td>${escapeHtml(p.agency.code)}</td><td><a href='/tax/periods/${p.id}'>${escapeHtml(p.periodStart)} → ${escapeHtml(p.periodEnd)}</a></td><td><span class='badge-pill'>${escapeHtml(p.status)}</span></td><td class='num'>${p.taxCollected? p.taxCollected : '$0'}</td><td class='num'>${p.taxItc? p.taxItc : '$0'}</td><td class='num'>${p.netOwing? p.netOwing : '$0'}</td></tr>`).join(''); 
                } 
            }
        } catch(e){ console.error('tax dashboard refresh failed', e); }
        finally{ setLoading(false); }
    }
    if(btn) btn.addEventListener('click', refresh);
})();