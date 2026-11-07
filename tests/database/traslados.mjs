// PostgreSQL real en WASM, aislado. No usa credenciales ni datos de producción.
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
const { PGlite } = await import(process.env.PGLITE_MODULE || '@electric-sql/pglite');
const db = new PGlite();
const root = new URL('../../', import.meta.url);
await db.exec(await readFile(new URL('tests/database/fixture_schema.sql', root), 'utf8'));
await db.exec(await readFile(new URL('database/changes/traslados_seguros.sql', root), 'utf8'));
await db.exec(await readFile(new URL('tests/database/fixture_acceso.sql', root), 'utf8'));
await db.exec(await readFile(new URL('database/changes/panel_y_acceso.sql', root), 'utf8'));
await db.exec(await readFile(new URL('tests/database/fixture_personas.sql', root), 'utf8'));
await db.exec(await readFile(new URL('database/changes/consulta_personas_y_altas.sql', root), 'utf8'));
await db.exec(await readFile(new URL('database/changes/administracion_cargos.sql', root), 'utf8'));
await db.exec(await readFile(new URL('database/changes/traslado_origen_esperado.sql', root), 'utf8'));
const id = n => `00000000-0000-0000-0000-${String(n).padStart(12, '0')}`;
const login = async n => db.query("select set_config('request.jwt.claim.sub',$1,false)", [n ? id(n) : '']);
const query = (s, p=[]) => db.query(s, p);
let tests = 0;
async function check(name, action) { await action(); tests++; console.log(`PASS ${name}`); }
async function denied(sql, params, pattern) {
    await assert.rejects(query(sql,params), pattern);
}
await db.exec(`
INSERT INTO public.distritos(id,codigo,nombre) VALUES ('${id(1)}','D1','Distrito 1'),('${id(2)}','D2','Distrito 2');
INSERT INTO public.congregaciones(id,distrito_id,codigo,nombre) VALUES
('${id(11)}','${id(1)}','C1','Origen'),('${id(12)}','${id(1)}','C2','Destino mismo distrito'),('${id(13)}','${id(2)}','C3','Otro distrito');
INSERT INTO public.personas(id,nombres,apellidos) VALUES
('${id(21)}','Admin','Nacional'),('${id(22)}','Admin','Distrito'),('${id(23)}','Admin','Local'),('${id(24)}','Sin','Permisos'),('${id(25)}','Persona','Trasladable');
INSERT INTO public.usuarios(id,persona_id,estado) VALUES
('${id(31)}','${id(21)}','activo'),('${id(32)}','${id(22)}','activo'),('${id(33)}','${id(23)}','activo'),('${id(34)}','${id(24)}','activo');
INSERT INTO public.cargos(id,codigo,nombre,nivel) VALUES
('${id(41)}','N','Nacional','nacional'),('${id(42)}','D','Distrital','distrital'),('${id(43)}','L','Local','local');
INSERT INTO public.asignaciones_cargos(persona_id,cargo_id,distrito_id,congregacion_id) VALUES
('${id(21)}','${id(41)}',NULL,NULL),('${id(22)}','${id(42)}','${id(1)}',NULL),('${id(23)}','${id(43)}',NULL,'${id(11)}');
INSERT INTO public.permisos(id,codigo,descripcion) VALUES
('${id(51)}','membresias.crear','Crear'),('${id(52)}','membresias.finalizar','Finalizar'),('${id(53)}','membresias.consultar','Consultar'),('${id(54)}','personas.consultar','Consultar personas');
INSERT INTO public.cargos_permisos SELECT c.id,p.id FROM public.cargos c CROSS JOIN public.permisos p;
`);
const transfer = "select public.trasladar_persona_app($1,$2,current_date,$3) as id";
const args = [id(25),id(12),'Prueba aislada'];
await check('destinos vacíos sin sesión', async()=>{
    await login(null); assert.equal((await query('select * from public.listar_destinos_membresia_app()')).rows.length,0);
});
await login(31);
await query("select public.registrar_membresia_app($1,$2,current_date-2,null)",[id(25),id(11)]);
await check('índice impide membresías activas duplicadas', async()=>{
    await denied("insert into public.membresias_congregacionales(persona_id,congregacion_id) values ($1,$2)",[id(25),id(12)],/duplicate key/);
});
await check('fecha anterior al ingreso rechazada sin cambio',async()=>{
    await denied("select public.trasladar_persona_app($1,$2,current_date-3,null)",args.slice(0,2),/anterior/);
});
await check('destino igual al origen rechazado',async()=>{
    await denied(transfer,[id(25),id(11),null],/ya pertenece/);
});
await check('fecha futura rechazada',async()=>{
    await denied("select public.trasladar_persona_app($1,$2,current_date+1,null)",args.slice(0,2),/fecha/);
});
await check('observaciones excesivas rechazadas',async()=>{await denied(transfer,[id(25),id(12),'x'.repeat(2001)],/2000/)});
await check('sin sesión no puede trasladar',async()=>{
    await login(null);await denied(transfer,args,/sesión/);
});
await check('usuario sin cargo no puede trasladar',async()=>{
    await login(34); await denied(transfer,args,/permiso/);
});
await check('local no puede registrar en otra congregación',async()=>{
    await login(33);assert.equal((await query('select * from public.listar_destinos_membresia_app()')).rows.length,1);
    await denied(transfer,args,/destino/);
});
await check('distrital no puede trasladar fuera del distrito',async()=>{
    await login(32);assert.equal((await query('select * from public.listar_destinos_membresia_app()')).rows.length,2);
    await denied(transfer,[id(25),id(13),null],/destino/);
});
await check('usuario suspendido no tiene permisos',async()=>{
    await login(31);await query("update public.usuarios set estado='suspendido' where id=$1",[id(31)]);
    await denied(transfer,args,/permiso/);
    await query("update public.usuarios set estado='activo' where id=$1",[id(31)]);
});
await check('fallo de inserción revierte salida y auditoría',async()=>{
    const before=(await query('select count(*)::int n from public.auditoria_membresias')).rows[0].n;
    await db.exec(`CREATE FUNCTION public.fallo_prueba() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fallo simulado'; END $$;
      CREATE TRIGGER fallo BEFORE INSERT ON public.membresias_congregacionales FOR EACH ROW EXECUTE FUNCTION public.fallo_prueba();`);
    await denied(transfer,args,/fallo simulado/);
    assert.equal((await query('select count(*)::int n from public.auditoria_membresias')).rows[0].n,before);
    assert.equal((await query("select congregacion_id from public.membresias_congregacionales where estado='activa'")).rows[0].congregacion_id,id(11));
    await db.exec('DROP TRIGGER fallo ON public.membresias_congregacionales; DROP FUNCTION public.fallo_prueba();');
});
await check('traslado distrital atómico y auditado',async()=>{
    await login(32);await query(transfer,args);
    const rows=(await query('select estado,congregacion_id from public.membresias_congregacionales order by fecha_registro')).rows;
    assert.equal(rows.filter(r=>r.estado==='activa').length,1);
    assert.equal(rows.find(r=>r.estado==='activa').congregacion_id,id(12));
    assert.equal(rows.filter(r=>r.estado==='finalizada').length,1);
    assert.equal((await query('select count(*)::int n from public.auditoria_membresias')).rows[0].n,4);
});
await check('reenvío del mismo traslado no crea duplicados',async()=>{await denied(transfer,args,/ya pertenece/)});
await check('nacional puede trasladar entre distritos',async()=>{
    await login(31);await query(transfer,[id(25),id(13),null]);
    assert.equal((await query("select count(*)::int n from public.membresias_congregacionales where estado='activa'")).rows[0].n,1);
});
await check('historial local no devuelve otras congregaciones',async()=>{
    await login(33);const rows=(await query('select * from public.listar_membresias_persona_app($1)',[id(25)])).rows;
    assert.ok(rows.length>0);assert.ok(rows.every(r=>r.congregacion_id===id(11)));
});
await check('rol anon no puede invocar el alta',async()=>{
    await db.exec('SET ROLE anon');
    await denied('select public.registrar_membresia_app($1,$2,current_date,null)',[id(25),id(11)],/permission denied/);
    await db.exec('RESET ROLE');
});
await check('panel local excluye congregaciones ajenas',async()=>{
    await login(33); const r=(await query('select public.resumen_territorial_app() r')).rows[0].r;
    assert.equal(r.congregaciones,1); assert.equal(r.activas,0); assert.equal(r.traslados,0);
    assert.equal(r.por_congregacion[0].congregacion,'Origen');
});
await check('panel nacional refleja movimientos reales',async()=>{
    await login(31); const r=(await query('select public.resumen_territorial_app() r')).rows[0].r;
    assert.equal(r.congregaciones,3); assert.equal(r.activas,1); assert.equal(r.traslados,2);
});
await check('perfil solo devuelve cargos del usuario',async()=>{
    await login(32); const r=(await query('select public.mi_acceso_app() r')).rows[0].r;
    assert.equal(r.cargos.length,1);assert.equal(r.cargos[0].nivel,'distrital');
});
await check('panel rechaza usuario sin cargo',async()=>{
    await login(34);await denied('select public.resumen_territorial_app()',[],/Acceso no autorizado/);
});
await check('listado de personas distingue historial local de membresía activa',async()=>{
    await login(33); const rows=(await query('select public.listar_personas_resumen_app() r')).rows;
    assert.equal(rows.length,1);assert.equal(rows[0].r.estado_membresia,'sin_activa_visible');
    await login(31);const nacional=(await query('select public.listar_personas_resumen_app() r')).rows;
    assert.equal(nacional[0].r.estado_membresia,'activa');
});
await check('alta local de persona fuera de su historial rechazada',async()=>{
    await login(33);await denied('select public.registrar_membresia_app($1,$2,current_date,null)',[id(24),id(11)],/territorio/);
});
await db.exec(`INSERT INTO public.permisos(id,codigo,descripcion) VALUES ('${id(55)}','usuarios.administrar','Administrar');
INSERT INTO public.cargos_permisos VALUES ('${id(41)}','${id(55)}');`);
await check('administración rechaza al usuario distrital',async()=>{
    await login(32);await denied('select public.listar_administracion_app()',[],/administración nacional/);
});
await check('administrador no puede suspenderse a sí mismo',async()=>{
    await login(31);await denied("select public.cambiar_estado_usuario_app($1,'suspendido')",[id(31)],/propio estado/);
});
await check('asignación respeta nivel territorial',async()=>{
    await login(31);await denied('select public.asignar_cargo_app($1,$2,$3,null,current_date,null,null)',[id(24),id(43),id(1)],/nivel del cargo/);
});
await check('asignar y finalizar cargo mantiene auditoría',async()=>{
    await login(31);const r=(await query('select public.asignar_cargo_app($1,$2,null,$3,current_date,null,null) id',[id(24),id(43),id(11)])).rows[0].id;
    await denied('select public.asignar_cargo_app($1,$2,null,$3,current_date,null,null)',[id(24),id(43),id(11)],/superpuesta/);
    await query('select public.finalizar_cargo_app($1,current_date)',[r]);
    assert.equal((await query('select count(*)::int n from public.auditoria_administracion')).rows[0].n,2);
});
await check('traslado rechaza origen modificado desde que se abrió la pantalla',async()=>{
    await login(31);await denied('select public.trasladar_persona_app($1,$2,current_date,null,$3)',[id(25),id(11),id(999)],/membresía cambió/);
});
await check('firma Android traslada con origen esperado y una sola RPC',async()=>{
    await login(31);const source=(await query("select id from public.membresias_congregacionales where persona_id=$1 and estado='activa'",[id(25)])).rows[0].id;
    await query('select public.trasladar_persona_app($1,$2,current_date,null,$3)',[id(25),id(11),source]);
    assert.equal((await query("select congregacion_id from public.membresias_congregacionales where persona_id=$1 and estado='activa'",[id(25)])).rows[0].congregacion_id,id(11));
});
// Recuperación territorial: pruebas aisladas de la migración de edición.
await db.exec(await readFile(new URL('database/changes/edicion_y_desactivacion_territorial_auditada.sql', root), 'utf8'));
await db.exec(`INSERT INTO public.permisos(id,codigo,descripcion) VALUES
('${id(56)}','congregaciones.editar','Editar'),('${id(57)}','distritos.crear','Administrar distritos');
INSERT INTO public.cargos_permisos VALUES ('${id(41)}','${id(56)}'),('${id(43)}','${id(56)}'),('${id(41)}','${id(57)}');`);
await check('selector local solo ofrece su distrito y congregación editable',async()=>{
    await login(33);
    const ds=(await query('select * from public.listar_distritos_congregaciones_app()')).rows;
    assert.deepEqual(ds.map(x=>x.id),[id(1)]);
    const cs=(await query('select * from public.listar_congregaciones_gestion_app($1)',[id(1)])).rows;
    assert.deepEqual(cs.map(x=>x.id),[id(11)]);
});
await check('edición fuera del territorio es rechazada',async()=>{
    await login(33);
    await denied("select public.actualizar_congregacion_app($1,'C3','Ajena','','','activa')",[id(13)],/permiso/);
});
await check('edición local conserva auditoría',async()=>{
    await login(33);
    await query("select public.actualizar_congregacion_app($1,'C1','Nombre actualizado','Municipio','Departamento','activa')",[id(11)]);
    const a=(await query("select datos_anteriores,datos_nuevos from public.auditoria_administracion where accion='editar_congregacion'")).rows;
    assert.equal(a.length,1);assert.equal(a[0].datos_anteriores.nombre,'Origen');assert.equal(a[0].datos_nuevos.nombre,'Nombre actualizado');
});
await check('no desactiva congregación con membresías vigentes',async()=>{
    await login(31);
    await denied("select public.actualizar_congregacion_app($1,'C1','Origen','','','inactiva')",[id(11)],/vigentes/);
    assert.equal((await query('select estado from public.congregaciones where id=$1',[id(11)])).rows[0].estado,'activa');
});
await check('no desactiva distrito con congregaciones activas',async()=>{
    await login(31);
    await denied("select public.actualizar_distrito_app($1,'D1','Distrito 1','','inactivo')",[id(1)],/desactive/);
});
console.log(`${tests} pruebas aprobadas. Base aislada eliminada al cerrar.`);
await db.close();
