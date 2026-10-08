(() => {
  const toggle=document.querySelector(".docs-nav-toggle"),sidebar=document.getElementById("docs-sidebar"),backdrop=document.querySelector(".docs-nav-backdrop");
  if (!toggle||!sidebar||!backdrop) return;
  let lastFocus=null;
  const setOpen=open=>{
    sidebar.classList.toggle("open",open);
    backdrop.classList.toggle("is-open",open);
    document.body.classList.toggle("docs-nav-open",open);
    toggle.setAttribute("aria-expanded",String(open));
    toggle.setAttribute("aria-label",open?"Close documentation menu":"Open documentation menu");
    if(open){lastFocus=document.activeElement;sidebar.querySelector("a")?.focus();}
    else if(sidebar.contains(document.activeElement))(lastFocus||toggle).focus();
  };
  toggle.addEventListener("click",()=>setOpen(toggle.getAttribute("aria-expanded")!=="true"));
  backdrop.addEventListener("click",()=>setOpen(false));
  sidebar.addEventListener("click",e=>{if(e.target.closest("a"))setOpen(false);});
  document.addEventListener("keydown",e=>{if(e.key==="Escape"&&toggle.getAttribute("aria-expanded")==="true")setOpen(false);});
  window.addEventListener("resize",()=>{if(innerWidth>768&&toggle.getAttribute("aria-expanded")==="true")setOpen(false);});
  document.querySelectorAll(".content table").forEach(table=>{
    if(table.closest(".table-scroll"))return;
    const wrapper=document.createElement("div");
    wrapper.className="table-scroll";wrapper.tabIndex=0;
    wrapper.setAttribute("role","region");wrapper.setAttribute("aria-label","Scrollable documentation table");
    table.before(wrapper);wrapper.append(table);
  });
  const version=document.querySelector("[data-kalo-version]");
  if(version)fetch("../version.json",{cache:"no-store"}).then(r=>{if(!r.ok)throw Error("Version unavailable");return r.json();}).then(d=>{if(d.name)version.textContent=d.name;}).catch(()=>{version.textContent="Latest release";});
})();
