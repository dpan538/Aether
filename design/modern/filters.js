// Custom ink menus mirror the selects used by the tuning encoder.
export function mountFilterMenus() {
  let open;
  const entries=[...document.querySelectorAll('.filters select,#ui-language')].map(select=>{
    const label=select.closest('label'),caption=label.querySelector('[data-i18n]');
    caption.id=`${select.id}-label`;
    select.hidden=true;
    select.setAttribute('aria-hidden','true');
    const trigger=document.createElement('button');
    trigger.type='button';trigger.className='filter-trigger';
    trigger.setAttribute('aria-haspopup','listbox');
    trigger.setAttribute('aria-expanded','false');
    trigger.setAttribute('aria-controls',`${select.id}-menu`);
    trigger.setAttribute('aria-label',select.getAttribute('aria-label'));
    trigger.innerHTML='<span></span><svg aria-hidden="true"><use href="#i-chevron"/></svg>';
    const menu=document.createElement('div');
    menu.className='filter-menu';menu.id=`${select.id}-menu`;menu.hidden=true;
    menu.setAttribute('role','listbox');menu.setAttribute('aria-labelledby',caption.id);
    label.append(trigger,menu);
    const entry={select,caption,trigger,menu,signature:''};
    trigger.addEventListener('click',()=>{
      if(select.disabled)return;
      if(open===entry){close();return;}
      close();open=entry;menu.hidden=false;trigger.setAttribute('aria-expanded','true');
    });
    trigger.addEventListener('keydown',event=>{
      if(event.key==='Escape'&&open===entry){event.preventDefault();close(true);return;}
      if(!['ArrowDown','ArrowUp'].includes(event.key)||select.disabled)return;
      event.preventDefault();close();open=entry;menu.hidden=false;trigger.setAttribute('aria-expanded','true');
      menu.querySelectorAll('[role=option]')[select.selectedIndex]?.focus();
    });
    menu.addEventListener('click',event=>{
      const option=event.target.closest('[data-option]');
      if(!option||select.disabled)return;
      select.selectedIndex=Number(option.dataset.option);
      select.dispatchEvent(new Event('change',{bubbles:true}));
      close(true);
    });
    menu.addEventListener('keydown',event=>{
      const options=[...menu.querySelectorAll('[role=option]')];
      const index=options.indexOf(document.activeElement);
      if(['ArrowDown','ArrowUp','Home','End'].includes(event.key)){
        event.preventDefault();
        const next=event.key==='Home'?0:event.key==='End'?options.length-1:(index+(event.key==='ArrowDown'?1:-1)+options.length)%options.length;
        options[next]?.focus();
      }else if(event.key==='Escape'){event.preventDefault();close(true);}
      else if(event.key==='Tab')close();
    });
    return entry;
  });
  function close(focus=false){
    if(!open)return;
    const entry=open;open=undefined;entry.menu.hidden=true;entry.trigger.setAttribute('aria-expanded','false');
    if(focus)entry.trigger.focus();
  }
  function sync(){
    for(const entry of entries){
      const {select,caption,trigger,menu}=entry;
      const signature=[caption.textContent,...[...select.options].map(option=>option.textContent)].join('|');
      if(entry.signature!==signature){
        entry.signature=signature;
        menu.replaceChildren(...[...select.options].map((option,index)=>{
          const item=document.createElement('button');item.type='button';item.tabIndex=-1;item.dataset.option=index;
          item.setAttribute('role','option');item.textContent=option.textContent;
          return item;
        }));
      }
      trigger.querySelector('span').textContent=select.selectedOptions[0].textContent;
      trigger.setAttribute('aria-label',`${select.getAttribute('aria-label')}：${select.selectedOptions[0].textContent}`);
      trigger.disabled=select.disabled;
      menu.querySelectorAll('[role=option]').forEach((item,index)=>item.setAttribute('aria-selected',String(index===select.selectedIndex)));
      if(open===entry&&select.disabled)close();
    }
  }
  const outside=event=>{if(open&&!open.trigger.parentElement.contains(event.target))close();};
  const viewChanged=()=>close();
  const observer=new MutationObserver(sync);
  entries.forEach(({select})=>observer.observe(select,{attributes:true,attributeFilter:['disabled']}));
  document.addEventListener('pointerdown',outside);
  document.addEventListener('aether:results',sync);
  document.addEventListener('aether:view',viewChanged);
  sync();
  return()=>{observer.disconnect();document.removeEventListener('pointerdown',outside);document.removeEventListener('aether:results',sync);document.removeEventListener('aether:view',viewChanged);};
}
