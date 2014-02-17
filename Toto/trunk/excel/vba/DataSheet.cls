VERSION 1.0 CLASS
BEGIN
  MultiUse = -1  'True
END
Attribute VB_Name = "DataSheet"
Attribute VB_GlobalNameSpace = False
Attribute VB_Creatable = False
Attribute VB_PredeclaredId = True
Attribute VB_Exposed = True
Private Function inData(Target As Range)
  For Each RName In ActiveWorkbook.Names
    If Left(RName.Name, 13) = "az_DataRegion" Then
       If Not Intersect(Target, Range(RName.Name)) Is Nothing Then
          inData = RName.Name
          Exit Function
       End If
    End If
  Next
 inData = ""
End Function

Private Function inHeadings(Target As Range)
  For Each RName In ActiveWorkbook.Names
    If Left(RName.Name, 24) = "az_DisplayColumnHeadings" Or Left(RName.Name, 21) = "az_DisplayRowHeadings" Then
       If Not Intersect(Target, Range(RName.Name)) Is Nothing Then
          inHeadings = RName.Name
          Exit Function
       End If
    End If
  Next
  inHeadings = ""
  

End Function

Private Sub Worksheet_BeforeDoubleClick(ByVal Target As Range, Cancel As Boolean)
Dim JSON As Object

 CheckConnectionId
  If (connectionid = "aborted") Then
     Exit Sub
  End If
  If (Target = "") Then
     Exit Sub
  End If
  
 ProvenanceString = ""
 If inHeadings(Target) > "" Then
  ProvenanceString = getProvenanceForName(Target)
 End If
  
  DataRegionName = inData(Target)
  If DataRegionName > "" Then
     ProvenanceString = getProvenanceForValue((Target.Column - Range(DataRegionName).Column), (Target.Row - Range(DataRegionName).Row), Mid(DataRegionName, 14, 1000))
  End If
  Set lib = New JSONLib
  Set JSON = lib.parse(ProvenanceString)
  Set JSONprov = JSON("provenance")
  provenance = "Provenance for " & Target.value
  provenance = "Provenance:" & vbCrLf & vbCrLf
  LastPerson = ""
  LastTime = ""
  For Each oneprov In JSONprov
    If oneprov("when") <> LastTime Or oneprov("who") <> LastPerson Then
      LastPerson = oneprov("who")
      LastTime = oneprov("when")
      provenance = provenance & "updated by: " & oneprov("who") & " at " & oneprov("when") & " - " & oneprov("how") & " " & oneprov("where") & vbCrLf
    End If
    provenance = provenance & vbCrLf & oneprov("value") & vbTab
    'provNames = oneprov("names")
    For Each provName In oneprov("names")
       provenance = provenance & provName & vbTab
    Next
  Next
  MsgBox (provenance)






End Sub


Function getProvenanceForName(ProvenanceName)
     getProvenanceForName = AzquoPost("Provenance", "name=" & ProvenanceName)
 End Function
 
Function getProvenanceForValue(Col, Row, Region)
     getProvenanceForValue = AzquoPost("Provenance", "col=" & Col & "&row=" & Row & "&region=" & Region)
 End Function

Sub selectSector()
   Call SelectItem("az_sectorchoice", "az_sectorchosen")

End Sub

Sub SelectItem(ChoiceRange, TargetRange)
  Dim NameChoice As String
  NameChoice = Replace(Range(ChoiceRange), """", "\""")
  
  azResponse = AzquoPost("Name", """operation"":""namelist"",""name"":""" & URLEncode(NameChoice) & """")
  SelectName.SelectName_Initialize
   SelectName.Show
  Range(TargetRange) = azNameChosen
End Sub


