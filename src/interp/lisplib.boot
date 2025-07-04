-- Copyright (c) 1991-2002, The Numerical ALgorithms Group Ltd.
-- All rights reserved.
--
-- Redistribution and use in source and binary forms, with or without
-- modification, are permitted provided that the following conditions are
-- met:
--
--     - Redistributions of source code must retain the above copyright
--       notice, this list of conditions and the following disclaimer.
--
--     - Redistributions in binary form must reproduce the above copyright
--       notice, this list of conditions and the following disclaimer in
--       the documentation and/or other materials provided with the
--       distribution.
--
--     - Neither the name of The Numerical ALgorithms Group Ltd. nor the
--       names of its contributors may be used to endorse or promote products
--       derived from this software without specific prior written permission.
--
-- THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
-- IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
-- TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
-- PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER
-- OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
-- EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
-- PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
-- PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
-- LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
-- NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
-- SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

)package "BOOT"

$printLoadMsgs := false

$spadLibFT := '"NRLIB"
$LISPLIB := false
$libFile := nil

$lisplibForm := nil
$lisplibKind := nil
$lisplibModemapAlist := []
$lisplibModemap := nil
$lisplibOperationAlist := []

--% Standard Library Creation Functions

readLib(fn) == kaf_open(fn, false)

writeLib(fn) == kaf_open(fn, true)
writeLib0(fn, ft) == kaf_open(make_filename2(fn, ft), true)

lisplibWrite(prop, val, lib_file) ==
  -- this may someday not write NIL keys, but it will now
  if $LISPLIB then
     kaf_write(lib_file, prop, val)

--% Loading

loadLibIfNotLoaded libName ==
  -- replaces old SpadCondLoad
  -- loads is library is not already loaded
  GET(libName, 'LOADED) => NIL
  loadLib libName

loadLib cname ==
  startTimingProcess 'load
  fullLibName := get_database(cname, 'OBJECT) or return nil
  systemdir? := is_system_path?(fullLibName)
  update? := not systemdir?
  loadLibNoUpdate1(cname, fullLibName)
  kind := get_database(cname, 'CONSTRUCTORKIND)
  if update? then
      updateCategoryTable(cname, kind)
  stopTimingProcess 'load

loadLibNoUpdate1(cname, fullLibName) ==
  if $printLoadMsgs then
    kind := get_database(cname, 'CONSTRUCTORKIND)
    say_msg("S2IL0002", '"Loading %1 for %2 %3b", [fullLibName, kind, cname])
  load_quietly(fullLibName)
  clearConstructorCache cname
  installConstructor(cname)
  MAKEPROP(cname,'LOADED,fullLibName)

loadLibNoUpdate(cname, fullLibName) ==
    startTimingProcess 'load
    loadLibNoUpdate1(cname, fullLibName)
    stopTimingProcess 'load

loadIfNecessary u == loadLibIfNecessary(u,true)

loadIfNecessaryAndExists u == loadLibIfNecessary(u,nil)

loadLibIfNecessary(u,mustExist) ==
  u = '$EmptyMode => u
  null atom u => loadLibIfNecessary(first u,mustExist)
  value:=
    functionp(u) or macrop(u) => u
    GET(u, 'LOADED) => u
    loadLib u => u
  null $InteractiveMode and ((null (y:= getProplist(u,$CategoryFrame)))
    or (null LASSOC('isFunctor,y)) and (null LASSOC('isCategory,y))) =>
      y:= get_database(u, 'CONSTRUCTORKIND) =>
         y = 'category =>
            updateCategoryFrameForCategory u
         updateCategoryFrameForConstructor u
      throw_msg("S2IL0005", '"%1bp is not a known type.", [u])
  value

convertOpAlist2compilerInfo(opalist) ==
   "append"/[[formatSig(op,sig) for sig in siglist]
                for [op,:siglist] in opalist] where
      formatSig(op, [typelist, slot,:stuff]) ==
          pred := if stuff then first stuff else 'T
          impl := if rest stuff then CADR stuff else 'ELT -- handles 'CONST
          [[op, typelist], pred, [impl, '%, slot]]

updateCategoryFrameForConstructor(constructor) ==
   opAlist := get_database(constructor, 'OPERATIONALIST)
   [[dc, :sig], [pred, impl]] :=
        get_database(constructor, 'CONSTRUCTORMODEMAP)
   $CategoryFrame := put(constructor,'isFunctor,
       convertOpAlist2compilerInfo(opAlist),
       addModemap(constructor, dc, sig, pred, impl,
           put(constructor, 'mode, ['Mapping,:sig], $CategoryFrame)))

updateCategoryFrameForCategory(category) ==
   di := get_database(category, 'CONSTRUCTORMODEMAP)
   if di then
       [[dc,:sig],[pred,impl]] := di
       $CategoryFrame :=
           addModemap(category, dc, sig, pred, impl, $CategoryFrame)
   $CategoryFrame := put(category, 'isCategory, 'T, $CategoryFrame)

loadFunctor u ==
  null atom u => loadFunctor first u
  loadLibIfNotLoaded u
  u

makeConstructorsAutoLoad() ==
  for cnam in allConstructors() repeat
    REMPROP(cnam,'LOADED)
    systemDependentMkAutoload(cnam,cnam)

systemDependentMkAutoload(fn,cnam) ==
    FBOUNDP(cnam) => "next"
    asharpName := get_database(cnam, 'ASHARP?) =>
         kind := get_database(cnam, 'CONSTRUCTORKIND)
         cosig := get_database(cnam, 'COSIG)
         file := get_database(cnam, 'OBJECT)
         SET_-LIB_-FILE_-GETTER(file, cnam)
         kind = 'category =>
              ASHARPMKAUTOLOADCATEGORY(file, cnam, asharpName, cosig)
         ASHARPMKAUTOLOADFUNCTOR(file, cnam, asharpName, cosig)
    spad_set_autoload(cnam)

autoLoad(cname) ==
  if not GET(cname, 'LOADED) then
      FMAKUNBOUND cname
      loadLib cname

--% Compilation

compDefineLisplib(df:=["DEF",[op,:.],:.],m,e,prefix,fal,fn) ==
  --fn= compDefineCategory OR compDefineFunctor
  sayMSG(filler_chars(72, '"-"))
  $LISPLIB: local := 'T
  $op: local := op
  $lisplibPredicates: local := NIL -- set by makePredicateBitVector
  $lisplibForm: local := NIL
  $lisplibKind: local := NIL
  $lisplibAbbreviation: local := NIL
  $lisplibAncestors: local := NIL
  $lisplibModemap: local := NIL
  $lisplibModemapAlist: local := NIL
  $lisplibOperationAlist: local := NIL
  $lisplibSuperDomain: local := NIL
  $libFile: local := NIL
  $lisplibCategory: local := nil
  $compiler_output_stream : local := nil
  --for categories, is rhs of definition; otherwise, is target of functor
  --will eventually become the "constructorCategory" property in lisplib
  --set in compDefineCategory1 if category, otherwise in finalizeLisplib
  libName := getConstructorAbbreviation op
  name := SNAME(libName)
  sayMSG ['"   initializing ",$spadLibFT,:bright libName,
    '"for",:bright op]
  -- following guarantee's compiler output files get closed.
  UNWIND_-PROTECT(
      PROGN(initializeLisplib(name),
            sayMSG ['"   compiling into ", $spadLibFT, :bright libName],
            res := FUNCALL(fn, df, m, e, prefix, fal),
            sayMSG ['"   finalizing ",$spadLibFT,:bright libName],
            finalizeLisplib($libFile)),
      PROGN(if $compiler_output_stream then CLOSE($compiler_output_stream),
            kaf_close($libFile)))
  lisplibDoRename(name)
  compile_lib(make_full_namestring(make_filename2(name, $spadLibFT)))
  FRESH_-LINE(get_algebra_stream())
  sayMSG(filler_chars(72, '"-"))
  merge_info_from_objects([get_database(op, 'ABBREVIATION)], [], false)
  $newConlist := [op, :$newConlist]  ---------->  bound in function "compiler"
  if $lisplibKind = 'category
    then updateCategoryFrameForCategory op
     else updateCategoryFrameForConstructor op
  res

initializeLisplib libName ==
  erase_lib0(libName, '"erlib")
  $libFile:= writeLib0(libName,'"erlib")
  $compiler_output_stream := make_compiler_output_stream($libFile, libName)

finalizeLisplib(libFile) ==
  lisplibWrite('"constructorForm", removeZeroOne($lisplibForm), libFile)
  lisplibWrite('"constructorKind", kind:=removeZeroOne $lisplibKind, libFile)
  lisplibWrite('"constructorModemap", removeZeroOne($lisplibModemap), libFile)
  $lisplibCategory:= $lisplibCategory or $lisplibModemap.mmTarget
  -- set to target of modemap for package/domain constructors;
  -- to the right-hand sides (the definition) for category constructors
  lisplibWrite('"constructorCategory", $lisplibCategory, libFile)
  lisplibWrite('"sourceFile", $edit_file, libFile)
  lisplibWrite('"modemaps",removeZeroOne $lisplibModemapAlist, libFile)
  ops := getConstructorOps($lisplibForm, kind)
  lisplibWrite('"operationAlist", removeZeroOne(ops), libFile)
  lisplibWrite('"superDomain", removeZeroOne($lisplibSuperDomain), libFile)
  lisplibWrite('"predicates", removeZeroOne($lisplibPredicates), libFile)
  lisplibWrite('"abbreviation", $lisplibAbbreviation, libFile)
  lisplibWrite('"ancestors", removeZeroOne($lisplibAncestors), libFile)
  lisplibWrite('"documentation", finalizeDocumentation(), libFile)

lisplibDoRename(libName) ==
    replace_lib(make_filename2(libName, '"erlib"),
                make_filename2(libName, $spadLibFT))

lisplibError(cname,fname,type,cn,fn,typ,error) ==
  $bootStrapMode and error = "wrongType" => nil
  sayMSG bright ['"  Illegal ",$spadLibFT]
  error in '(duplicateAbb  wrongType) =>
    say_msg("S2IL0007", CONCAT(
        '"%1b claims that its constructor name is the %2 %3b but %3b is",
        '" already known to be the %d for %4 %5b .:"),
        [[fname,$spadLibFT], type, cname, typ, cn])
  error is 'abbIsName =>
      throw_msg("S2IL0008",
          '"%1b is the name of a %2 constructor from %3b .",
          [fname, typ, [fn,$spadLibFT]])

getPartialConstructorModemapSig(c) ==
  (s := getConstructorSignature c) => rest s
  throwEvalTypeMsg("S2IL0015",[c])

getConstructorOps(form, kind) ==
    kind is 'category => getCategoryOps(form)
    getFunctorOps(form)

getCategoryOps(catForm) ==
    -- returns operations of first catForm
    transformOperationAlist getSlot1FromCategoryForm(catForm)

getFunctorOps(form) ==
    transformOperationAlist $lisplibOperationAlist

transformOperationAlist operationAlist ==
  --  this transforms the operationAlist which is written out onto LISPLIBs.
  --  The original form of this list is a list of items of the form:
  --        ((<op> <signature>) (<condition> (ELT $ n)))
  --  The new form is an op-Alist which has entries (<op> . signature-Alist)
  --      where signature-Alist has entries (<signature> . item)
  --        where item has form (<slotNumber> <condition> <kind>)
  --          where <kind> =
  --             NIL  => function
  --             CONST => constant ... and others
  newAlist:= nil
  for [[op,sig,:.],condition,implementation] in operationAlist repeat
    kind:=
      implementation is [eltEtc,.,n] and eltEtc in '(CONST ELT) => eltEtc
      implementation is [impOp,:.] =>
        impOp = 'XLAM => implementation
        impOp = CONST => impOp
        keyedSystemError("S2IL0025",[impOp])
      implementation = 'mkRecord => 'mkRecord
      keyedSystemError("S2IL0025",[implementation])
    signatureItem:=
      if u:= assoc([op,sig],$functionLocations) then n := [n,:rest u]
      kind = 'ELT =>
        condition = 'T => [sig,n]
        [sig,n,condition]
      [sig,n,condition,kind]
    itemList := insert(signatureItem, QLASSQ(op, newAlist))
    newAlist:= insertAlist(op,itemList,newAlist)
  newAlist

getConstructorModemap form ==
    get_database(opOf(form), 'CONSTRUCTORMODEMAP)

getConstructorSignature form ==
  (mm := get_database(opOf(form), 'CONSTRUCTORMODEMAP)) =>
    [[.,:sig],:.] := mm
    sig
  NIL

getSlot1FromCategoryForm ([op, :argl]) ==
  u:= eval [op,:MAPCAR('MKQ,TAKE(#argl,$FormalMapVariableList))]
  null VECP u =>
    systemErrorHere '"getSlot1FromCategoryForm"
  u.1


--% constructor evaluation
--  The following functions are used by the compiler but are modified
--  here for use with new LISPLIB scheme

mkEvalableCategoryForm(c, e) ==       --from DEFINE
  c is [op,:argl] =>
    op="Join" =>
        nargs := [mkEvalableCategoryForm(x, e) or return nil for x in argl]
        nargs => ["JoinInner", ["LIST", :nargs]]
    op is "DomainSubstitutionMacro" =>
        mkEvalableCategoryForm(CADR argl, e)
    op is "mkCategory" => c
    MEMQ(op,$CategoryNames) =>
        [x, m, e] := compOrCroak(c, $EmptyMode, e)
        m=$Category => optFunctorBody x
    get_database(op, 'CONSTRUCTORKIND) = 'category or
      get(op,"isCategory",$CategoryFrame) =>
        [op,:[quotifyCategoryArgument x for x in argl]]
    [x, m, e] := compOrCroak(c, $EmptyMode, e)
    m=$Category => x
  MKQ c

isDomainForm(D,e) ==
  --added for MPOLY 3/83 by RDJ
  MEMQ(IFCAR D, $SpecialDomainNames) or isFunctor D or
    -- ((D is ['Mapping,target,:.]) and isCategoryForm(target)) or
     ((getmode(D, e) is ['Mapping, target, :.]) and isCategoryForm(target)) or
       isCategoryForm(getmode(D, e)) or isDomainConstructorForm(D, e)

isDomainConstructorForm(D,e) ==
  D is [op,:argl] and (u:= get(op,"value",e)) and
    u is [.,["Mapping",target,:.],:.] and
      isCategoryForm(EQSUBSTLIST(argl, $FormalMapVariableList, target))

isFunctor x ==
  op:= opOf x
  not IDENTP op => false
  $InteractiveMode =>
    MEMQ(op,'(Union SubDomain Mapping Record)) => true
    MEMQ(get_database(op, 'CONSTRUCTORKIND),'(domain package))
  u:= get(op,'isFunctor,$CategoryFrame)
    or MEMQ(op,'(SubDomain Union Record)) => u
  constructor? op =>
    prop := get(op,'isFunctor,$CategoryFrame) => prop
    if get_database(op, 'CONSTRUCTORKIND) = 'category
      then updateCategoryFrameForCategory op
      else updateCategoryFrameForConstructor op
    get(op,'isFunctor,$CategoryFrame)
  nil
