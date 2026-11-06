# Gera arquitetura.html com o diagrama SVG da plataforma.
E=[]  # elementos svg
def box(x,y,w,h,titulo,sub,exp="int",sub2=None):
    E.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="6" class="box exp-{exp}"/>')
    cx=x+w/2
    ty = y+h/2-(9 if sub2 else 4)
    E.append(f'<text x="{cx}" y="{ty}" class="t">{titulo}</text>')
    E.append(f'<text x="{cx}" y="{ty+16}" class="s">{sub}</text>')
    if sub2: E.append(f'<text x="{cx}" y="{ty+30}" class="s">{sub2}</text>')
def pill(x,y,w,h,txt):
    E.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{h/2}" class="ator"/>')
    E.append(f'<text x="{x+w/2}" y="{y+h/2+5}" class="t">{txt}</text>')
def seta(pts,cls="",num=None,npos=None,dash=False):
    d=" ".join(f"{x},{y}" for x,y in pts)
    extra=' stroke-dasharray="5 4"' if dash else ''
    m={"f1":"a1","f2":"a2","f3":"a3"}.get(cls,"a0")
    E.append(f'<polyline points="{d}" class="seta {cls}" marker-end="url(#{m})"{extra}/>')
    if num:
        if npos is None:
            (x1,y1),(x2,y2)=pts[0],pts[-1]; npos=((x1+x2)/2,(y1+y2)/2)
        nx,ny=npos
        E.append(f'<circle cx="{nx}" cy="{ny}" r="10" class="num {cls}"/>')
        E.append(f'<text x="{nx}" y="{ny+4}" class="nt">{num}</text>')
def rot(x,y,txt,anchor="middle",cls="r"):
    E.append(f'<text x="{x}" y="{y}" class="{cls}" text-anchor="{anchor}">{txt}</text>')

# atores
pill(380,14,150,40,"Aluno")
pill(1222,690,128,40,"Administrador")
# VM
E.append('<rect x="20" y="78" width="1180" height="760" rx="10" class="vm"/>')
rot(780,104,"VM cloudlab · Proxmox · Ubuntu 24.04 · 4 vCPU / 6 GB",anchor="start",cls="grupo")
rot(780,122,"fora do contorno tracejado: Docker Compose (serviços de apoio)",anchor="start",cls="r")
# linha 1
box(60,140,220,60,"Keycloak","OIDC · realm feedback · :8180","lan")
box(60,220,220,40,"PostgreSQL","rede interna","int")
box(360,140,380,60,"Kong Gateway","jwt · rate-limit 30/min · CORS · correlation-id","lan",":8000")
# faasd
E.append('<rect x="40" y="282" width="1140" height="218" rx="10" class="faasd"/>')
rot(56,302,"faasd · containerd (namespace openfaas)",anchor="start",cls="grupo")
box(60,314,850,44,"Gateway OpenFaaS","127.0.0.1:8080 · scale_from_zero · idler zera réplicas após 15 min","lo")
box(960,314,200,44,"Prometheus","127.0.0.1:9090","lo")
box(60,410,160,64,"receber-avaliacao","valida · classifica","int","persiste · publica")
box(260,410,160,64,"NATS JetStream","stream AVALIACOES","int","retenção 30 dias")
box(500,410,160,64,"notificar-critico","só CRITICA","int","idempotente")
box(790,410,160,64,"gerar-relatorio","semanal · 7 dias","int","CSV + JSON")
box(1010,410,150,64,"cron-connector","0 11 * * 1 (UTC)","int","= seg 08:00 BRT")
# apoio abaixo
box(670,524,110,56,"Mailpit","SMTP :1025","dock","UI :8025 (LAN)")
box(60,640,860,60,"ScyllaDB Alternator (API DynamoDB) · 172.17.0.1:8200","avaliacoes: PK AVALIACAO#dia · SK timestamp#uuid","dock","notificacoes: PK id (deduplicação)")
box(980,640,180,60,"MinIO (API S3)","bucket privado · :9000","dock","console :9001 (LAN)")
box(60,754,200,50,"Alloy","lê o journald do host","int")
box(320,754,200,50,"Loki","127.0.0.1:3100","lo")
box(960,754,200,50,"Grafana","dashboard · alertas · :3000","lan")

# Fluxo 1 (recebimento)
seta([(420,54),(190,140)],"f1","1")
seta([(480,54),(480,140)],"f1","2")
seta([(550,200),(550,314)],"f1","3",npos=(550,257))
seta([(140,358),(140,410)],"f1")
seta([(110,474),(110,640)],"f1","4",npos=(110,557))
seta([(220,442),(260,442)],"f1","5",npos=(240,426))
# gateway -> funções (invocação)
seta([(580,358),(580,410)],"")
seta([(870,358),(870,410)],"")
seta([(910,336),(960,336)],"")
# Fluxo 2 (alerta)
seta([(420,442),(500,442)],"f2","6",npos=(460,426))
rot(460,494,"nats-connector")
seta([(560,474),(560,640)],"f2","7",npos=(560,557))
seta([(640,474),(690,524)],"f2","8",npos=(652,505))
# Fluxo 3 (relatório)
seta([(1010,442),(950,442)],"f3","9",npos=(980,426))
seta([(850,474),(850,640)],"f3","10",npos=(850,557))
seta([(930,474),(1030,640)],"f3","11",npos=(985,565))
seta([(810,474),(760,524)],"f3","12",npos=(800,505))
# observabilidade
seta([(40,460),(30,460),(30,779),(60,779)],"",dash=True)
rot(36,620,"stdout JSON → journald",anchor="start",cls="r vert")
seta([(260,779),(320,779)],"")
seta([(520,779),(960,779)],"")
rot(740,772,"logs (LogQL)")
seta([(1160,336),(1170,336),(1170,726),(1060,726),(1060,754)],"")
rot(1166,600,"métricas",anchor="end")
# administrador
seta([(1222,720),(1160,690)],"",dash=True)
seta([(1222,716),(1160,779)],"",dash=True)
rot(1286,750,"lê alertas no",cls="r")
rot(1286,764,"Mailpit UI :8025",cls="r")

svg=f'''<svg viewBox="0 0 1360 852" role="img" aria-label="Arquitetura da plataforma: o aluno obtém token no Keycloak e envia a avaliação ao Kong; o Kong encaminha ao gateway do faasd, que invoca receber-avaliacao; ela grava no ScyllaDB e publica no NATS JetStream; o nats-connector invoca notificar-critico, que deduplica no Scylla e envia e-mail pelo Mailpit; o cron-connector dispara gerar-relatorio-semanal, que faz 7 consultas no Scylla, grava CSV e JSON no MinIO e envia o resumo por e-mail; logs vão do journald ao Loki pelo Alloy e métricas do Prometheus ao Grafana." xmlns="http://www.w3.org/2000/svg">
<defs>
<marker id="a0" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" class="ponta"/></marker>
<marker id="a1" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" class="ponta f1"/></marker>
<marker id="a2" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" class="ponta f2"/></marker>
<marker id="a3" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" class="ponta f3"/></marker>
</defs>
{chr(10).join(E)}
</svg>'''
import pathlib; aqui=pathlib.Path(__file__).parent
tpl=(aqui/"modelo.html").read_text()
(aqui/"arquitetura.html").write_text(tpl.replace("<!--SVG-->",svg))
