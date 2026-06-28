(function(){
    const btn = document.getElementById('period-refresh');
    const spinner = document.getElementById('period-spinner');
    const idMatch = window.location.pathname.match(/\/tax\/periods\/(\d+)/);
    const collectedEl = document.getElementById('period-tax-collected');
    const itcEl = document.getElementById('period-itc');
    const netEl = document.getElementById('period-net');
    function setLoading(on){ if(spinner) spinner.style.display = on ? '' : 'none'; if(btn) btn.disabled = on; }
    function fmt(v){ try{ return new Intl.NumberFormat(undefined,{style:'currency',currency:'CAD'}).format(v); }catch(e){ return v; } }
    async function refresh(){ if(!idMatch) return; setLoading(true); try{ const id = idMatch[1]; const r = await fetch(`/api/tax/periods/${id}`,{cache:'no-store'}); if(r.ok){ const p = await r.json(); if(collectedEl) collectedEl.textContent = fmt(p.taxCollected || 0); if(itcEl) itcEl.textContent = fmt(p.taxITC || 0); if(netEl) netEl.textContent = fmt(p.netOwing || 0); } }catch(e){ console.error('period refresh failed', e);} finally{ setLoading(false);} }
    if(btn) btn.addEventListener('click', refresh);
    setInterval(refresh,30000);
})();