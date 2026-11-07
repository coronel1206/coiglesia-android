-- Solo para la base aislada de pruebas. Nunca ejecutar en producción.
CREATE SCHEMA auth;
CREATE ROLE anon; CREATE ROLE authenticated;
CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS $$
SELECT NULLIF(current_setting('request.jwt.claim.sub', true), '')::uuid;
$$;
GRANT USAGE ON SCHEMA auth TO anon, authenticated;
GRANT EXECUTE ON FUNCTION auth.uid() TO anon, authenticated;
CREATE TABLE public.distritos (
id uuid DEFAULT gen_random_uuid() NOT NULL,
codigo character varying NOT NULL,
nombre character varying NOT NULL,
descripcion text,
estado character varying DEFAULT 'activo'::character varying NOT NULL CHECK (estado::text = ANY (ARRAY['activo'::character varying, 'inactivo'::character varying]::text[])),
fecha_creacion timestamp with time zone DEFAULT now() NOT NULL);
ALTER TABLE public.distritos ENABLE ROW LEVEL SECURITY;
CREATE TABLE public.congregaciones (
id uuid DEFAULT gen_random_uuid() NOT NULL,
distrito_id uuid NOT NULL,
codigo character varying NOT NULL,
nombre character varying NOT NULL,
municipio character varying,
departamento character varying,
fecha_fundacion date,
estado character varying DEFAULT 'activa'::character varying NOT NULL CHECK (estado::text = ANY (ARRAY['activa'::character varying, 'inactiva'::character varying, 'cerrada'::character varying]::text[])),
fecha_creacion timestamp with time zone DEFAULT now() NOT NULL);
ALTER TABLE public.congregaciones ENABLE ROW LEVEL SECURITY;
CREATE TABLE public.obras_proyeccion (
id uuid DEFAULT gen_random_uuid() NOT NULL,
congregacion_origen_id uuid NOT NULL,
codigo character varying NOT NULL,
nombre character varying NOT NULL,
municipio character varying,
departamento character varying,
direccion_referencia text,
fecha_apertura date DEFAULT CURRENT_DATE NOT NULL,
estado character varying DEFAULT 'activa'::character varying NOT NULL CHECK (estado::text = ANY (ARRAY['activa'::character varying, 'en_evaluacion'::character varying, 'administracion_distrital'::character varying, 'constituida'::character varying, 'cerrada'::character varying]::text[])),
observaciones text,
fecha_registro timestamp with time zone DEFAULT now() NOT NULL);
ALTER TABLE public.obras_proyeccion ENABLE ROW LEVEL SECURITY;
CREATE TABLE public.personas (
id uuid DEFAULT gen_random_uuid() NOT NULL,
nombres character varying NOT NULL,
apellidos character varying NOT NULL,
tipo_documento character varying,
numero_documento character varying,
telefono character varying,
correo_electronico character varying,
fecha_nacimiento date,
estado character varying DEFAULT 'activo'::character varying NOT NULL CHECK (estado::text = ANY (ARRAY['activo'::character varying, 'inactivo'::character varying]::text[])),
fecha_registro timestamp with time zone DEFAULT now() NOT NULL);
ALTER TABLE public.personas ENABLE ROW LEVEL SECURITY;
CREATE TABLE public.cargos (
id uuid DEFAULT gen_random_uuid() NOT NULL,
codigo character varying NOT NULL,
nombre character varying NOT NULL,
nivel character varying NOT NULL CHECK (nivel::text = ANY (ARRAY['nacional'::character varying, 'distrital'::character varying, 'local'::character varying]::text[])),
descripcion text,
activo boolean DEFAULT true NOT NULL,
fecha_creacion timestamp with time zone DEFAULT now() NOT NULL);
ALTER TABLE public.cargos ENABLE ROW LEVEL SECURITY;
CREATE TABLE public.asignaciones_cargos (
id uuid DEFAULT gen_random_uuid() NOT NULL,
persona_id uuid NOT NULL,
cargo_id uuid NOT NULL,
distrito_id uuid,
congregacion_id uuid,
fecha_inicio date DEFAULT CURRENT_DATE NOT NULL,
fecha_fin date,
estado character varying DEFAULT 'activo'::character varying NOT NULL CHECK (estado::text = ANY (ARRAY['activo'::character varying, 'suspendido'::character varying, 'finalizado'::character varying]::text[])),
observaciones text,
fecha_registro timestamp with time zone DEFAULT now() NOT NULL);
ALTER TABLE public.asignaciones_cargos ENABLE ROW LEVEL SECURITY;
CREATE TABLE public.usuarios (
id uuid NOT NULL,
persona_id uuid NOT NULL,
estado character varying DEFAULT 'pendiente'::character varying NOT NULL CHECK (estado::text = ANY (ARRAY['pendiente'::character varying, 'activo'::character varying, 'suspendido'::character varying, 'bloqueado'::character varying]::text[])),
fecha_creacion timestamp with time zone DEFAULT now() NOT NULL,
ultimo_acceso timestamp with time zone);
ALTER TABLE public.usuarios ENABLE ROW LEVEL SECURITY;
CREATE TABLE public.permisos (
id uuid DEFAULT gen_random_uuid() NOT NULL,
codigo character varying NOT NULL,
descripcion text NOT NULL,
fecha_creacion timestamp with time zone DEFAULT now() NOT NULL);
ALTER TABLE public.permisos ENABLE ROW LEVEL SECURITY;
CREATE TABLE public.cargos_permisos (
cargo_id uuid NOT NULL,
permiso_id uuid NOT NULL);
ALTER TABLE public.cargos_permisos ENABLE ROW LEVEL SECURITY;
CREATE TABLE public.notificaciones_distritales (
id uuid DEFAULT gen_random_uuid() NOT NULL,
distrito_id uuid NOT NULL,
obra_id uuid NOT NULL,
tipo character varying DEFAULT 'nueva_obra'::character varying NOT NULL,
titulo character varying NOT NULL,
mensaje text NOT NULL,
fecha_creacion timestamp with time zone DEFAULT now() NOT NULL,
fecha_lectura timestamp with time zone);
ALTER TABLE public.notificaciones_distritales ENABLE ROW LEVEL SECURITY;
CREATE TABLE public.historial_administracion_obras (
id uuid DEFAULT gen_random_uuid() NOT NULL,
obra_id uuid NOT NULL,
estado_anterior character varying NOT NULL,
estado_nuevo character varying NOT NULL,
motivo text NOT NULL,
usuario_responsable_id uuid NOT NULL,
fecha_cambio timestamp with time zone DEFAULT now() NOT NULL);
ALTER TABLE public.historial_administracion_obras ENABLE ROW LEVEL SECURITY;
CREATE TABLE public.nombramientos_pastorales_obras (
id uuid DEFAULT gen_random_uuid() NOT NULL,
obra_id uuid NOT NULL,
pastor_persona_id uuid NOT NULL,
fecha_inicio date DEFAULT CURRENT_DATE NOT NULL,
fecha_fin date,
estado character varying DEFAULT 'activo'::character varying NOT NULL CHECK (estado::text = ANY (ARRAY['activo'::character varying, 'finalizado'::character varying, 'revocado'::character varying]::text[])),
motivo text NOT NULL,
autorizado_por uuid NOT NULL,
fecha_autorizacion timestamp with time zone DEFAULT now() NOT NULL,
fecha_registro timestamp with time zone DEFAULT now() NOT NULL);
ALTER TABLE public.nombramientos_pastorales_obras ENABLE ROW LEVEL SECURITY;
CREATE TABLE public.auditoria_nombramientos (
id uuid DEFAULT gen_random_uuid() NOT NULL,
nombramiento_id uuid NOT NULL,
obra_id uuid NOT NULL,
accion character varying NOT NULL CHECK (accion::text = ANY (ARRAY['CREACION'::character varying, 'MODIFICACION'::character varying, 'ELIMINACION'::character varying]::text[])),
datos_anteriores jsonb,
datos_nuevos jsonb,
usuario_responsable_id uuid,
fecha_evento timestamp with time zone DEFAULT now() NOT NULL);
ALTER TABLE public.auditoria_nombramientos ENABLE ROW LEVEL SECURITY;
CREATE TABLE public.credenciales_ministeriales (
id uuid DEFAULT gen_random_uuid() NOT NULL,
persona_id uuid NOT NULL,
numero_credencial character varying,
tipo character varying NOT NULL CHECK (tipo::text = ANY (ARRAY['pastoral'::character varying, 'ministerial'::character varying, 'licencia'::character varying, 'otra'::character varying]::text[])),
fecha_expedicion date NOT NULL,
fecha_vencimiento date,
estado character varying DEFAULT 'vigente'::character varying NOT NULL CHECK (estado::text = ANY (ARRAY['vigente'::character varying, 'suspendida'::character varying, 'revocada'::character varying, 'vencida'::character varying]::text[])),
observaciones text,
fecha_registro timestamp with time zone DEFAULT now() NOT NULL);
ALTER TABLE public.credenciales_ministeriales ENABLE ROW LEVEL SECURITY;
CREATE TABLE public.restricciones_ministeriales (
id uuid DEFAULT gen_random_uuid() NOT NULL,
persona_id uuid NOT NULL,
tipo character varying NOT NULL CHECK (tipo::text = ANY (ARRAY['inhabilitacion_pastoral'::character varying, 'suspension_ministerial'::character varying, 'limitacion_administrativa'::character varying, 'otra'::character varying]::text[])),
alcance character varying NOT NULL CHECK (alcance::text = ANY (ARRAY['nombramiento_pastoral'::character varying, 'ejercicio_ministerial'::character varying, 'cargo_administrativo'::character varying, 'general'::character varying]::text[])),
fecha_inicio date NOT NULL,
fecha_fin date,
estado character varying DEFAULT 'activa'::character varying NOT NULL CHECK (estado::text = ANY (ARRAY['activa'::character varying, 'levantada'::character varying, 'finalizada'::character varying]::text[])),
referencia_decision character varying,
observaciones text,
fecha_registro timestamp with time zone DEFAULT now() NOT NULL);
ALTER TABLE public.restricciones_ministeriales ENABLE ROW LEVEL SECURITY;
CREATE TABLE public.membresias_congregacionales (
id uuid DEFAULT gen_random_uuid() NOT NULL,
persona_id uuid NOT NULL,
congregacion_id uuid NOT NULL,
fecha_ingreso date DEFAULT CURRENT_DATE NOT NULL,
fecha_salida date,
estado character varying DEFAULT 'activa'::character varying NOT NULL CHECK (estado::text = ANY (ARRAY['activa'::character varying, 'finalizada'::character varying]::text[])),
observaciones text,
fecha_registro timestamp with time zone DEFAULT now() NOT NULL);
ALTER TABLE public.membresias_congregacionales ENABLE ROW LEVEL SECURITY;
CREATE TABLE public.auditoria_membresias (
id uuid DEFAULT gen_random_uuid() NOT NULL,
persona_id uuid NOT NULL,
membresia_id uuid,
congregacion_origen_id uuid,
congregacion_destino_id uuid,
accion character varying NOT NULL CHECK (accion::text = ANY (ARRAY['registro'::character varying, 'finalizacion'::character varying, 'traslado'::character varying]::text[])),
datos_anteriores jsonb,
datos_nuevos jsonb,
observaciones text,
usuario_responsable_id uuid,
fecha_evento timestamp with time zone DEFAULT now() NOT NULL);
ALTER TABLE public.auditoria_membresias ENABLE ROW LEVEL SECURITY;
CREATE UNIQUE INDEX idx_membresia_persona_activa ON public.membresias_congregacionales(persona_id) WHERE estado='activa';
CREATE OR REPLACE FUNCTION public.finalizar_membresia_app(p_membresia_id uuid, p_fecha_salida date DEFAULT CURRENT_DATE, p_observaciones text DEFAULT NULL::text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
v_persona_id uuid;
v_congregacion_id uuid;
v_distrito_id uuid;
v_fecha_ingreso date;
v_fecha_salida date;
BEGIN
IF p_membresia_id IS NULL THEN
RAISE EXCEPTION
'Debe indicar la membresia';
END IF;

SELECT
m.persona_id,
m.congregacion_id,
c.distrito_id,
m.fecha_ingreso
INTO
v_persona_id,
v_congregacion_id,
v_distrito_id,
v_fecha_ingreso
FROM public.membresias_congregacionales m
JOIN public.personas p
ON p.id = m.persona_id
JOIN public.congregaciones c
ON c.id = m.congregacion_id
JOIN public.distritos d
ON d.id = c.distrito_id
WHERE m.id = p_membresia_id
AND m.estado = 'activa'
AND p.estado = 'activo'
AND c.estado = 'activa'
AND d.estado = 'activo'
FOR UPDATE OF m;

IF v_persona_id IS NULL THEN
RAISE EXCEPTION
'Membresia activa no encontrada';
END IF;

IF NOT public.tiene_permiso_territorial_seguro(
'membresias.finalizar',
v_distrito_id,
v_congregacion_id
) THEN
RAISE EXCEPTION
'No tiene permiso para finalizar esta membresia';
END IF;

v_fecha_salida := COALESCE(
p_fecha_salida,
CURRENT_DATE
);

IF v_fecha_salida < v_fecha_ingreso THEN
RAISE EXCEPTION
'La fecha de salida no puede ser anterior al ingreso';
END IF;

IF v_fecha_salida > CURRENT_DATE THEN
RAISE EXCEPTION
'No se permite una fecha de salida futura';
END IF;

IF length(COALESCE(p_observaciones, '')) > 2000 THEN
RAISE EXCEPTION
'Las observaciones superan los 2000 caracteres';
END IF;

UPDATE public.membresias_congregacionales
SET
estado = 'finalizada',
fecha_salida = v_fecha_salida,
observaciones = CASE
WHEN NULLIF(btrim(p_observaciones), '') IS NULL
THEN observaciones
ELSE btrim(p_observaciones)
END
WHERE id = p_membresia_id
AND estado = 'activa';

IF NOT FOUND THEN
RAISE EXCEPTION
'No fue posible finalizar la membresia';
END IF;

RETURN p_membresia_id;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.listar_membresias_persona_app(p_persona_id uuid)
RETURNS TABLE(membresia_id uuid, persona_id uuid, congregacion_id uuid, congregacion_nombre text, distrito_id uuid, distrito_nombre text, fecha_ingreso date, fecha_salida date, estado text, observaciones text)
LANGUAGE plpgsql
STABLE SECURITY DEFINER
SET search_path TO ''
AS $function$
BEGIN

IF p_persona_id IS NULL THEN
RAISE EXCEPTION 'Debe indicar una persona';
END IF;

IF NOT EXISTS (
SELECT 1
FROM public.personas p
WHERE p.id = p_persona_id
AND p.estado = 'activo'
) THEN
RAISE EXCEPTION 'Persona activa no encontrada';
END IF;

-- Autorizar por territorio, sin exigir membresía activa.
IF NOT EXISTS (
SELECT 1
FROM public.membresias_congregacionales m
JOIN public.congregaciones c
ON c.id = m.congregacion_id
JOIN public.distritos d
ON d.id = c.distrito_id
WHERE m.persona_id = p_persona_id
AND public.tiene_permiso_territorial_seguro(
'membresias.consultar',
d.id,
c.id
)
) THEN
RAISE EXCEPTION
'No tiene autorización para consultar esta persona';
END IF;

-- Devolver todas las membresías autorizadas.
RETURN QUERY
SELECT
m.id,
m.persona_id,
c.id,
c.nombre::text,
d.id,
d.nombre::text,
m.fecha_ingreso,
m.fecha_salida,
m.estado::text,
m.observaciones
FROM public.membresias_congregacionales m
JOIN public.congregaciones c
ON c.id = m.congregacion_id
JOIN public.distritos d
ON d.id = c.distrito_id
WHERE m.persona_id = p_persona_id
AND public.tiene_permiso_territorial_seguro(
'membresias.consultar',
d.id,
c.id
)
ORDER BY
m.fecha_ingreso DESC,
m.fecha_registro DESC;

END;
$function$
;

CREATE OR REPLACE FUNCTION public.registrar_auditoria_membresia()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $function$
DECLARE
v_usuario_id uuid;
v_accion varchar(30);
v_origen uuid;
v_destino uuid;
BEGIN
-- Identificar al usuario autenticado
SELECT u.id
INTO v_usuario_id
FROM public.usuarios u
WHERE u.id = (SELECT auth.uid())
AND u.estado = 'activo';

IF v_usuario_id IS NULL THEN
RAISE EXCEPTION
'No se pudo identificar al usuario responsable de la operación';
END IF;

IF TG_OP = 'INSERT' THEN
v_accion := 'registro';
v_origen := NULL;
v_destino := NEW.congregacion_id;

INSERT INTO public.auditoria_membresias (
persona_id,
membresia_id,
congregacion_origen_id,
congregacion_destino_id,
accion,
datos_anteriores,
datos_nuevos,
observaciones,
usuario_responsable_id
)
VALUES (
NEW.persona_id,
NEW.id,
v_origen,
v_destino,
v_accion,
NULL,
to_jsonb(NEW),
NEW.observaciones,
v_usuario_id
);

RETURN NEW;

ELSIF TG_OP = 'UPDATE'
AND OLD.estado = 'activa'
AND NEW.estado = 'finalizada' THEN

v_accion := 'finalizacion';
v_origen := OLD.congregacion_id;
v_destino := NULL;

INSERT INTO public.auditoria_membresias (
persona_id,
membresia_id,
congregacion_origen_id,
congregacion_destino_id,
accion,
datos_anteriores,
datos_nuevos,
observaciones,
usuario_responsable_id
)
VALUES (
NEW.persona_id,
NEW.id,
v_origen,
v_destino,
v_accion,
to_jsonb(OLD),
to_jsonb(NEW),
NEW.observaciones,
v_usuario_id
);

RETURN NEW;
END IF;

RETURN NEW;
END;
$function$
;

CREATE OR REPLACE FUNCTION public.tiene_permiso_territorial(p_codigo_permiso text, p_distrito_id uuid DEFAULT NULL::uuid, p_congregacion_id uuid DEFAULT NULL::uuid)
RETURNS boolean
LANGUAGE sql
STABLE SECURITY DEFINER
SET search_path TO ''
AS $function$
SELECT
(
p_distrito_id IS NULL
OR p_congregacion_id IS NULL
OR EXISTS (
SELECT 1
FROM public.congregaciones cg
WHERE cg.id = p_congregacion_id
AND cg.distrito_id = p_distrito_id
)
)
AND EXISTS (
SELECT 1
FROM public.usuarios u
JOIN public.asignaciones_cargos ac
ON ac.persona_id = u.persona_id
JOIN public.cargos c
ON c.id = ac.cargo_id
JOIN public.cargos_permisos cp
ON cp.cargo_id = c.id
JOIN public.permisos p
ON p.id = cp.permiso_id
WHERE u.id = auth.uid()
AND u.estado = 'activo'
AND ac.estado = 'activo'
AND ac.fecha_inicio <= CURRENT_DATE
AND (
ac.fecha_fin IS NULL
OR ac.fecha_fin >= CURRENT_DATE
)
AND c.activo = TRUE
AND p.codigo = p_codigo_permiso
AND (
(
c.nivel = 'nacional'
AND ac.distrito_id IS NULL
AND ac.congregacion_id IS NULL
)
OR
(
c.nivel = 'distrital'
AND p_distrito_id IS NOT NULL
AND ac.distrito_id = p_distrito_id
AND ac.congregacion_id IS NULL
)
OR
(
c.nivel = 'local'
AND p_congregacion_id IS NOT NULL
AND ac.congregacion_id = p_congregacion_id
AND ac.distrito_id IS NULL
)
)
);
$function$
;

CREATE OR REPLACE FUNCTION public.tiene_permiso_territorial_seguro(p_codigo_permiso text, p_distrito_id uuid DEFAULT NULL::uuid, p_congregacion_id uuid DEFAULT NULL::uuid)
RETURNS boolean
LANGUAGE sql
STABLE SECURITY DEFINER
SET search_path TO ''
AS $function$
SELECT
public.tiene_permiso_territorial(
p_codigo_permiso,
p_distrito_id,
p_congregacion_id
)

AND EXISTS (
SELECT 1
FROM public.usuarios u
JOIN public.personas p
ON p.id = u.persona_id
WHERE u.id = (SELECT auth.uid())
AND u.estado = 'activo'
AND p.estado = 'activo'
)

AND (
p_distrito_id IS NULL
OR EXISTS (
SELECT 1
FROM public.distritos d
WHERE d.id = p_distrito_id
AND d.estado = 'activo'
)
)

AND (
p_congregacion_id IS NULL
OR EXISTS (
SELECT 1
FROM public.congregaciones c
JOIN public.distritos d
ON d.id = c.distrito_id
WHERE c.id = p_congregacion_id
AND c.estado = 'activa'
AND d.estado = 'activo'
AND (
p_distrito_id IS NULL
OR d.id = p_distrito_id
)
)
);
$function$
;
CREATE TRIGGER trg_auditoria_membresia_insert AFTER INSERT ON public.membresias_congregacionales FOR EACH ROW EXECUTE FUNCTION public.registrar_auditoria_membresia();
CREATE TRIGGER trg_auditoria_membresia_finalizacion AFTER UPDATE OF estado ON public.membresias_congregacionales FOR EACH ROW WHEN (OLD.estado='activa' AND NEW.estado='finalizada') EXECUTE FUNCTION public.registrar_auditoria_membresia();


ALTER TABLE public.usuarios ADD PRIMARY KEY(id);
