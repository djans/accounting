(function(){
    const btn = document.getElementById('agency-refresh');
    const spinner = document.getElementById('agency-spinner');
    const container = document.getElementById('agency-report');
    const agencyId = container ? container.getAttribute('data-agency-id') : null;

    function setLoading(on){ if(spinner) spinner.style.display = on ? '' : 'none'; if(btn) btn.disabled = on; }
    function formatCurrency(v){ try{ const n = Number(v); return new Intl.NumberFormat(undefined, {style:'currency', currency:'CAD'}).format(isNaN(n)?0:n); }catch(e){return v;} }
    function escapeHtml(s){ if(s==null) return ''; return String(s).replace(/[&<>"']/g, function(m){ return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]; }); }

    async function refresh(){
        if(!agencyId) return;
        setLoading(true);
        try{
            const r = await fetch(`/api/tax-agencies/${agencyId}/report`, {cache:'no-store'});
            if(r.ok){
                const data = await r.json();
                
                // Update stats
                const statValues = document.querySelectorAll('.stat-value');
                if(statValues.length >= 3) {
                    statValues[0].textContent = formatCurrency(data.totalCollected);
                    statValues[1].textContent = formatCurrency(data.totalItc);
                    statValues[2].textContent = formatCurrency(data.totalNet);
                }

                // Update table
                const tbody = document.querySelector('table.table-portal tbody');
                if(tbody && data.periods) {
                    if(data.periods.length === 0) {
                        const portalCard = tbody.closest('.portal-card');
                        const emptyState = portalCard.querySelector('.empty-state');
                        if(emptyState) emptyState.style.display = 'block';
                        tbody.closest('table').style.display = 'none';
                    } else {
                        const portalCard = tbody.closest('.portal-card');
                        const emptyState = portalCard.querySelector('.empty-state');
                        if(emptyState) emptyState.style.display = 'none';
                        tbody.closest('table').style.display = 'table';
                        
                        tbody.innerHTML = data.periods.map(p => `
                            <tr>
                                <td><a href="/tax/periods/${p.id}">${escapeHtml(p.periodStart)} → ${escapeHtml(p.periodEnd)}</a></td>
                                <td><span class="badge-pill">${escapeHtml(p.status)}</span></td>
                                <td class="num">${formatCurrency(p.taxCollected)}</td>
                                <td class="num">${formatCurrency(p.taxItc)}</td>
                                <td class="num">${formatCurrency(p.netOwing)}</td>
                                <td>${escapeHtml(p.filedDate || '—')}</td>
                                <td>${escapeHtml(p.paidDate || '—')}</td>
                            </tr>
                        `).join('');
                    }
                }
            }
        } catch(e) {
            console.error('Agency report refresh failed', e);
        } finally {
            setLoading(false);
        }
    }

    if(btn) btn.addEventListener('click', refresh);
})();
