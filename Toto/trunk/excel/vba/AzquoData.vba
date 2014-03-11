Attribute VB_Name = "AzquoData"
'This code, and all code associated with the Azquo workbook, copyright Azquo Ltd 2013 (except where specified).  Programmed by Bill Cawley

Option Explicit






Sub ZapSheet(SheetName)
   Dim SheetNo As Worksheet
   
   For Each SheetNo In ActiveWorkbook.Worksheets
      If SheetNo.Name = SheetName Then
         Application.DisplayAlerts = False
          SheetNo.Delete
          Application.DisplayAlerts = True
       End If
    Next
End Sub

Sub AddSheet(SheetName)
    ZapSheet (SheetName)
    Sheets.Add
    ActiveSheet.Name = SheetName
End Sub

  
  
  
 
Function CheckRange(RangeName As String)

  If Not rangeExists(RangeName) Then
    CheckRange = False
    MsgBox ("Range " & RangeName & " not found")
    End
  End If
End Function

Function checkWidth(ArraySent)
   pos = 0
   eol = Find("\n", ArraySent)
   If (eol < 0) Then
      eol = Len(ArraySent)
   End If
   Count = 1
   While (Find("\t", ArraySent, pos) > 0 And pos < eol)
     pos = Find("\t", ArraySent, pos) + 1
     Count = Count + 1
  Wend
  checkWidth = Count
End Function

Function checkHeight(ArraySent)
   pos = 0
   Count = 1
   While (Find("\n", ArraySent, pos) > 0 And pos < eol)
     pos = Find("\t", ArraySent, pos) + 1
     Count = Count + 1
  Wend
  checkHeight = Count
End Function




Sub LoadData()
  Dim RName As Name
  Dim Region As String
  Dim updatedCell As Range
  
  
  For Each RName In ActiveWorkbook.Names
     If Left(RName.Name, 13) = "az_DataRegion" Then
        'Set updatedCell = Cells(Range(RName.Name).Row - 1, Range("az_HideColumn").Column)
        'don't update if updated within the last ten minutes
        'If DateDiff("n", updatedCell, Now()) > 10 Then
             Region = Mid(RName.Name, 14, 1000)
             LoadDataRegion (Region)
             'updatedCell = Now()
         ' End If
     End If
  Next

End Sub

Sub ShowProgress(Region, Activity)
  Application.ScreenUpdating = True
  If rangeExists("az_UpdateRegion") Then
    Range("az_UpdateRegion") = Region & " " & Activity
  End If
  Application.ScreenUpdating = False



End Sub

Sub LoadDataRegion(Region As String)
'CheckConnectionId
  
  Call ShowProgress(Region, "")
  Application.ScreenUpdating = False
  
  ClearDataRegion (Region)
  azError = FillRowHeadings(Region)
  If azError = "" Then
     azError = FillColumnHeadings(Region)
  End If
  If azError = "" Then
     azError = FillData(Region)
     If azError = "" Then
       Call ShowProgress(Region, "Options")
       If hasOption(Region, "hiderows") > "" Then
         HideRows (Region)
       End If
       If hasOption(Region, "Percentage") > "" Then
         PercentageRows (Region)
       End If
       If hasOption(Region, "sort") > "" Then
         SortRows (Region)
       End If
       If hasOption(Region, "maxrows") > "" Then
         MaxRows (Region)
       End If
      TrimRowHeadings (Region)
       TrimColumnHeadings (Region)
       SetChartData (Region)
     End If
     Call ShowProgress(Region, "Complete")
  End If
  
  ActiveWorkbook.Names("copyregion").Delete
  
  ZapSheet ("Azquo Clipboard")
  Application.ScreenUpdating = True
End Sub

Function GetHideColumn()

   If Not rangeExists("az_HideColumn") Then
      MsgBox ("To hide blank rows, you need a hidden column to the left of the screen with a cell range named 'az_HideColumn'.  Put a formula in this column which, if zero, will hide the row")
      End
   End If
   GetHideColumn = Range("az_HideColumn").Column


End Function

Sub HideRows(Region)
   Dim DataRegion As Range
   Dim DataRows, RowNo, LastRow As Integer
   Dim strHideCount As String
   Dim HideCount As Integer
   
   strHideCount = hasOption(Region, "hiderows")
   If (strHideCount <> "True") Then
      HideCount = strHideCount
   Else
      HideCount = 1
   End If
   
   Dim TotalCol, VisibleRow As Integer
   Dim Total, Count
   TotalCol = GetHideColumn()
   Set DataRegion = Range("az_DataRegion" & Region)
   VisibleRow = 0
   DataRows = DataRegion.Rows.Count
   RowNo = DataRegion.Row
   LastRow = DataRegion.Row + DataRows - 1
   While RowNo <= LastRow
      
      Total = 0
      For Count = 0 To HideCount - 1
        Total = Total + Cells(RowNo + Count, TotalCol)
      Next
       If Total = 0 Then
         'never delete the bottom row, or leave less than 3 rows
         If DataRows <= 3 Or DataRows <= HideCount Or RowNo = LastRow Then
           For Count = 0 To HideCount - 1
              Rows(RowNo + Count).EntireRow.Hidden = True
           Next
        Else
           For Count = 0 To HideCount - 1
             Rows(RowNo).Delete
           Next
           LastRow = LastRow - HideCount
           DataRows = DataRows - HideCount
           RowNo = RowNo - HideCount
         End If
      End If
      RowNo = RowNo + HideCount
   Wend
End Sub

Sub PercentageRows(Region)
   Dim RowNo, TotalRow, ColNo As Integer
   Dim Total
   Dim DataRegion As Range
   
   Set DataRegion = Range("az_DataRegion" & Region)
   TotalRow = DataRegion.Row + DataRegion.Rows.Count
   For ColNo = DataRegion.Column To DataRegion.Column + DataRegion.Columns.Count - 1
      If Cells(TotalRow, ColNo) > 0 Then
          Total = Cells(TotalRow, ColNo)
          For RowNo = DataRegion.Row To TotalRow - 1
            Cells(RowNo, ColNo) = Cells(RowNo, ColNo) / Total
          Next
      End If
    Next
   
End Sub

Sub SortRows(Region)
   Dim DataRegion As Range
   Dim DataRows As Integer
   Dim SortRegion As Range
   
   Dim TotalCol As Integer
   TotalCol = GetHideColumn()
   Set DataRegion = Range("az_DataRegion" & Region)
   Set SortRegion = Range(Cells(DataRegion.Row, TotalCol), Cells(DataRegion.Row + DataRegion.Rows.Count - 1, DataRegion.Column + DataRegion.Columns.Count - 1))
   If hasOption(Region, "sort desc") > "" Then
      SortRegion.Sort Key1:=Cells(DataRegion.Row, TotalCol), Order1:=xlDescending
   Else
      SortRegion.Sort Key1:=Cells(DataRegion.Row, TotalCol), Order1:=xlAscending
   End If
End Sub

Sub MaxRows(Region)

  Dim strRowCount As String
  Dim RowCount As Integer
  Dim DataRegion As Range
  
  strRowCount = hasOption(Region, "maxrows")
  RowCount = strRowCount
  Set DataRegion = Range("az_DataRegion" & Region)
  If DataRegion.Rows.Count > RowCount Then
     Rows((DataRegion.Row + RowCount) & ":" & (DataRegion.Row + DataRegion.Rows.Count - 1)).EntireRow.Delete
  End If
  
  
End Sub


Sub TrimRowHeadings(Region)
   Dim RowHeadings As Range
   Dim RowNo, ColNo, VisibleRow As Integer
   
   If Not rangeExists("az_DisplayRowHeadings" & Region) Then
      Exit Sub
   End If
   Set RowHeadings = Range("az_DisplayRowHeadings" & Region)
   For ColNo = RowHeadings.Column To RowHeadings.Column + RowHeadings.Columns.Count - 1
      VisibleRow = RowHeadings.Row - 1
      For RowNo = RowHeadings.Row To RowHeadings.Row + RowHeadings.Rows.Count - 1
         If Rows(RowNo).EntireRow.Hidden = False Then
            If Cells(RowNo, ColNo) = Cells(VisibleRow, ColNo) Then
              Cells(RowNo, ColNo).ClearContents
            Else
               VisibleRow = RowNo
            End If
            
         End If
      Next
    Next
    
         


End Sub


Sub TrimColumnHeadings(Region)
   Dim ColumnHeadings As Range
   Dim RowNo, ColNo, VisibleColumn As Integer
   If Not rangeExists("az_DisplayColumnHeadings" & Region) Then
      Exit Sub
   End If
   Set ColumnHeadings = Range("az_DisplayColumnHeadings" & Region)
   For RowNo = ColumnHeadings.Row To ColumnHeadings.Row + ColumnHeadings.Rows.Count - 1
      VisibleColumn = ColumnHeadings.Column - 1
      For ColNo = ColumnHeadings.Column To ColumnHeadings.Column + ColumnHeadings.Columns.Count - 1
            If Cells(RowNo, ColNo) = Cells(RowNo, VisibleColumn) Then
              Cells(RowNo, ColNo).ClearContents
            Else
               VisibleColumn = ColNo
            End If
      Next
    Next
    
         


End Sub





Function FillRowHeadings(Region)
     
     Dim thisSheet As Worksheet
     Dim DisplayRows As Range
     
     Call ShowProgress(Region, "Row headings")
     CheckRange ("az_RowHeadings" & Region)
     'CheckRange ("az_DisplayRowHeadings" & Region)
     
     azResponse = AzquoPost("Value", "rowheadings=" & RangeText("az_RowHeadings" & Region) & "&region=" & Region)
     If Not rangeExists("az_DisplayRowHeadings" & Region) Then
        Exit Function
     End If
     'CopyDataRegionToClipboard (Region)
     Set thisSheet = ActiveSheet
    
     Call FillTheRange(azResponse, "az_DisplayRowHeadings" & Region)
     Set DisplayRows = Range("az_DisplayRowHeadings" & Region)
     Dim MyData As DataObject

     Set MyData = New DataObject
     MyData.SetText azResponse
     MyData.PutInClipboard
     
     DisplayRows.Select
     Application.DisplayAlerts = False
     ActiveSheet.PasteSpecial Format:="Text"
     'CopyClipboardBack (Region)
     Application.DisplayAlerts = True
     FillRowHeadings = ""
     Rows(DisplayRows.Row & ":" & DisplayRows.Row + DisplayRows.Rows.Count - 1).EntireRow.AutoFit
End Function


Function FillColumnHeadings(Region)
     Dim NoColumns, ColCount, FirstInsert, LastInsert, FirstRemove, LastRemove As Integer
     Dim DIsplayColumns As Range
    
     Call ShowProgress(Region, "Column headings")
     CheckRange ("az_ColumnHeadings" & Region)
     azResponse = AzquoPost("Value", "columnheadings=" & RangeText("az_ColumnHeadings" & Region) & "&region=" & Region)
     If Not rangeExists("az_DisplayColumnHeadings" & Region) Then
       Exit Function
     End If
      'CopyDataRegionToClipboard (Region)
     Dim ColumnHeadings As Variant
     If InStr(azResponse, Chr(10)) > 0 Then
        ColumnHeadings = Split(Left(azResponse, InStr(azResponse, Chr(10))), Chr(9))
     Else
         ColumnHeadings = Split(azResponse, Strings.Chr(9))
     End If
     NoColumns = UBound(ColumnHeadings) + 1
     Set DIsplayColumns = Range("az_DisplayColumnHeadings" & Region)
     If DIsplayColumns.Columns.Count < NoColumns Then
         FirstInsert = DIsplayColumns.Column + DIsplayColumns.Columns.Count - 1
         LastInsert = DIsplayColumns.Column + NoColumns - 2
         Range(Columns(FirstInsert), Columns(LastInsert)).Insert
     End If
     ColCount = DIsplayColumns.Columns.Count
     If NoColumns < 3 Then
        NoColumns = 3
     End If
     If ColCount > NoColumns Then
         LastRemove = DIsplayColumns.Column + ColCount - 2
         FirstRemove = DIsplayColumns.Column + NoColumns - 1
         Range(Columns(FirstRemove), Columns(LastRemove)).Delete
     End If
     Dim MyData As DataObject

     Set MyData = New DataObject
     MyData.SetText azResponse
     MyData.PutInClipboard
     
     DIsplayColumns.Select
     Application.DisplayAlerts = False
     ActiveSheet.PasteSpecial Format:="Text"
     'CopyClipboardBack (Region)
     Application.DisplayAlerts = True
     FillColumnHeadings = ""
     Rows(DIsplayColumns.Row & ":" & DIsplayColumns.Row + DIsplayColumns.Rows.Count - 1).EntireRow.AutoFit
     

End Function

Function FillData(Region)
    Dim Context, DataRegion As Range
 
     Call ShowProgress(Region, "Data")
     CheckRange ("az_Context" & Region)
     CheckRange ("az_DataRegion" & Region)
     Context = RangeText("az_Context" & Region)
     azResponse = AzquoPost("Value", "context=" & Context & "&region=" & Region)
      
     ' copy the formulae in the data region to another sheet, so as to copy them back after the data region has been filled
     CopyDataRegionToClipboard (Region)
     Set DataRegion = Range("az_DataRegion" & Region)
     DataRegion.Select
     
     ' insert the data
     
     Dim MyData As DataObject
     Set MyData = New DataObject
     MyData.SetText azResponse
     MyData.PutInClipboard
     Application.DisplayAlerts = False
     ActiveSheet.PasteSpecial Format:="Text"
     
     'now copy back the formulae and formats
     CopyClipboardBack (Region)
     Application.DisplayAlerts = True
     FillData = ""
     Cells(1, 1).Select

End Function

Function CopyDataRegionToClipboard(Region)
   Dim thisSheet, CopySheet As Worksheet
   Dim DataRegion As Range

    Set thisSheet = ActiveSheet
    AddSheet ("Azquo Clipboard")
     Set CopySheet = Sheets("Azquo Clipboard")
     Set DataRegion = Range("az_DataRegion" & Region)
     DataRegion.Copy
     ActiveSheet.Cells(1, 1).Select
     ActiveSheet.Paste
      ActiveWorkbook.Names.Add Name:="copyregion", RefersToR1C1:= _
        "='Azquo Clipboard'!R1C1:R" & DataRegion.Rows.Count & "C" & DataRegion.Columns.Count
        
     thisSheet.Activate
End Function

Function CopyClipboardBack(Region)
    Dim thisSheet, CopySheet As Worksheet
    
    Set thisSheet = ActiveSheet
    Set CopySheet = Sheets("Azquo Clipboard")
    CopySheet.Activate
     Range("copyregion").Copy
     thisSheet.Activate
     
     Range("az_DataRegion" & Region).Select
     Selection.PasteSpecial Paste:=xlPasteFormulas, SkipBlanks:=True
     Selection.PasteSpecial Paste:=xlPasteFormats
     ZapSheet ("Azquo clipboard")

End Function





Function RangeText(RangeName)
     Dim azRange As Range
     Dim RowNo, ColNo As Integer
     Dim azTopLeft As Range
     
     Set azRange = Range(RangeName)
     RangeText = ""
     Set azTopLeft = azRange.Worksheet.Cells(azRange.Row, azRange.Column)
     
     For RowNo = 0 To azRange.Rows.Count - 1
        If (RowNo > 0) Then
           RangeText = RangeText & Chr(10)
        End If
        For ColNo = 0 To azRange.Columns.Count - 1
           If (ColNo > 0) Then
              RangeText = RangeText & Chr(9)
           End If
           RangeText = RangeText & azTopLeft.Offset(RowNo, ColNo).value
        Next
     Next
     RangeText = URLEncode(RangeText)
End Function

Sub SaveData()
  Dim RName As Name
  Dim Region As String
  Dim updatedCell As Range
  
  
  For Each RName In ActiveWorkbook.Names
     If Left(RName.Name, 13) = "az_DataRegion" Then
        'Set updatedCell = Cells(Range(RName.Name).Row - 1, Range("az_HideColumn").Column)
        'don't update if updated within the last ten minutes
        'If DateDiff("n", updatedCell, Now()) > 10 Then
             Region = Mid(RName.Name, 14, 1000)
             SaveRegionData (Region)
             'updatedCell = Now()
         ' End If
     End If
  Next




End Sub
Function SaveRegionData(Region)
     Dim DataText As String
     DataText = GetTextFromRangeText(Range("az_DataRegion" & Region))
     azResponse = AzquoPost("Value", "editeddata=" & DataText & "&region=" & Region)



End Function
Function ClearRange(RangeName As String)
   If rangeExists(RangeName) Then
     Range(RangeName).ClearContents
   End If
End Function

Function rangeExists(RangeName As String)
   Dim RName As Name
   
   For Each RName In ActiveWorkbook.Names
     If RName.Name = RangeName Then
        rangeExists = True
        Exit Function
     End If
   Next
   rangeExists = False
        
End Function
Function getLeftInsertColumn() As Integer
   If Not rangeExists("az_TopLeft") Then
     getLeftInsertColumn = 0
   Else
     getLeftInsertColumn = Range("az_TopLeft").Column
  End If
End Function
Function hasOption(Region, OptionName)
      Dim Options As String
      Dim FoundPos As Integer
      
      If rangeExists("az_Options" & Region) Then
         Options = Range("az_Options" & Region).value
         FoundPos = InStr(LCase(Options), LCase(OptionName))
         If FoundPos <= 0 Then
            hasOption = ""
            Exit Function
          End If
          hasOption = Trim(Mid(Options, FoundPos + Len(OptionName), 1000))
          FoundPos = InStr(hasOption, ",")
          If FoundPos > 0 Then
            hasOption = Trim(Left(hasOption, FoundPos - 1))
           End If
          'remove any '=' char
          hasOption = Trim(Replace(hasOption, "=", ""))
          If hasOption = "" Then
             hasOption = "True"
          End If
          Exit Function
        End If
        hasOption = ""
End Function

Sub zapName(RangeName)
Dim RName As Name

   For Each RName In ActiveWorkbook.Names
     If RName.Name = RangeName Then
        RName.Delete
        Exit Sub
     End If
   Next
End Sub

Sub ClearData()
  Dim RName As Name
  Dim Region As String
  Dim updatedCell As Range
  zapName ("copyregion")
  
  For Each RName In ActiveWorkbook.Names
     If Left(RName.Name, 13) = "az_DataRegion" Then
             Region = Mid(RName.Name, 14, 1000)
             ClearDataRegion (Region)
     End If
  Next

End Sub


Sub ClearDataRegion(Region As String)
     
     Dim DataRegion As Range
     Dim FirstRemove As Integer
     Dim LastRemove As Integer
     Dim LeftColumn As Integer
     
     LeftColumn = getLeftInsertColumn()
     Set DataRegion = Range("az_DataRegion" & Region)
     
     DataRegion.EntireRow.Hidden = False
     If rangeExists("az_DisplayRowHeadings" & Region) Then
         If DataRegion.Rows.Count > 3 Then
             LastRemove = DataRegion.Row + DataRegion.Rows.Count - 2
             FirstRemove = DataRegion.Row + 2
             If LeftColumn > 0 Then
                Range(Cells(FirstRemove, LeftColumn), Cells(LastRemove, 100)).Delete Shift:=xlUp
             Else
                 Rows(FirstRemove & ":" & LastRemove).Delete
             End If
         End If
         ClearRange ("az_DisplayRowHeadings" & Region)
      End If
      If rangeExists("az_DisplayColumnHeadings" & Region) Then
         If hasOption(Region, "trimcols") > "" Then
         
             If DataRegion.Columns.Count > 3 Then
                 LastRemove = DataRegion.Column + DataRegion.Columns.Count - 2
                 FirstRemove = DataRegion.Column + 2
                 Range(Columns(FirstRemove), Columns(LastRemove)).Delete
             End If
         End If
         ClearRange ("az_DisplayColumnHeadings" & Region)
      End If
     ' copy the formulae
     Set DataRegion = Range("az_DataRegion" & Region)
      
     DataRegion.Copy
     DataRegion.ClearContents
     'DataRegion.PasteSpecial Paste:=xlPasteFormulasAndNumberFormats
     
  
End Sub



' off the internet and modified
Function GetTextFromRangeText(ByVal poRange As Range) As String
    Dim vRange As Variant
    Dim sRet As String
    Dim i As Integer
    Dim j As Integer
    Dim firstline As Boolean
    Dim firstcell As Boolean

    If Not poRange Is Nothing Then

        vRange = poRange
        
        firstline = True
        For i = LBound(vRange) To UBound(vRange)
            firstcell = True
            For j = LBound(vRange, 2) To UBound(vRange, 2)
            If firstcell Then
                If firstline Then
                    sRet = vRange(i, j)
                Else
                    sRet = sRet & vbCrLf & vRange(i, j)
                End If
            Else
                sRet = sRet & vbTab & vRange(i, j)
            End If
                firstcell = False
            Next j
            firstline = False
        Next i
    End If

    GetTextFromRangeText = sRet
End Function

Sub SetChartData(Region)
  Dim ColHeadings, DataRegion As Range
  Dim RegionLeft, RegionRight, topLine, DataTop, DataBottom, RangeString As String
  Dim ChartName As String
  Dim ThisChart As Chart
  Dim Plotby As XlRowCol
  
  ChartName = hasOption(Region, "Chart")
  If ChartName > "" Then
    Set ColHeadings = Range("az_DisplayColumnHeadings" & Region)
    Set DataRegion = Range("az_DataRegion" & Region)
    RegionLeft = "$" & Chr(63 + DataRegion.Column)
    RegionRight = "$" & Chr(63 + DataRegion.Column + DataRegion.Columns.Count)
    topLine = "$" & ColHeadings.Row
    DataTop = "$" & DataRegion.Row
    DataBottom = "$" & (DataRegion.Row + DataRegion.Rows.Count - 1)
  
    RangeString = RegionLeft & topLine & ":" & RegionRight & topLine & ","
    RangeString = RangeString + RegionLeft & DataTop & ":" & RegionRight & DataBottom
    Set ThisChart = ActiveSheet.ChartObjects(ChartName).Chart
    Plotby = ThisChart.Plotby
    If Plotby <> xlRows And Plotby <> xlColumns Then
      'this should not happen!!!
      Plotby = xlRows
    End If
    ThisChart.SetSourceData Source:=Range(RangeString)
    ThisChart.Plotby = Plotby
  End If
End Sub



Private Function inData(Target As Range)
Dim RName As Name
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
  Dim RName As Name
  
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

Sub DoubleClick(ByVal Target As Range, Cancel As Boolean)
Dim JSON, JSONProv, oneProv As Object
Dim lib As JSONLib
Dim ProvenanceString, DataRegionName, provenance, LastPerson, LastTime As String
Dim provName As Variant

 CheckConnectionId
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
  Set JSONProv = JSON.Item("provenance")
  provenance = "Provenance for " & Target.value
  provenance = "Provenance:" & vbCrLf & vbCrLf
  LastPerson = ""
  LastTime = ""
  For Each oneProv In JSONProv
    If oneProv("when") <> LastTime Or oneProv("who") <> LastPerson Then
      LastPerson = oneProv("who")
      LastTime = oneProv("when")
      provenance = provenance & "updated by: " & oneProv("who") & " at " & oneProv("when") & " - " & oneProv("how") & " " & oneProv("where") & vbCrLf
    End If
    provenance = provenance & vbCrLf & oneProv("value") & vbTab
    'provNames = oneprov("names")
    For Each provName In oneProv.Item("names")
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

Sub SelectItem(ChoiceRange, TargetRange)
  Dim NameChoice As String
  NameChoice = Replace(Range(ChoiceRange), """", "\""")
  
  azResponse = AzquoPost("Name", """operation"":""namelist"",""name"":""" & URLEncode(NameChoice) & """")
  SelectName.SelectName_Initialize
   SelectName.Show
  Range(TargetRange) = azNameChosen
End Sub


Sub ButtonSelect()
   Dim ButtonPressed As String
   Dim RowNo, ThisCol As Integer
   Dim NameChosen As String
   Dim RName, RNameSaved As Name
   Dim Nearest As Integer
   
   zapName ("copyregion")
   ButtonPressed = Application.Caller
   RowNo = ActiveSheet.Shapes(ButtonPressed).BottomRightCell.Row
   Nearest = 0
   For Each RName In ActiveWorkbook.Names
       ThisCol = RName.RefersToRange.Column
       If RName.RefersToRange.Worksheet Is ActiveSheet And RName.RefersToRange.Row = RowNo And ThisCol < ActiveSheet.Shapes(ButtonPressed).BottomRightCell.Column And ThisCol > Nearest Then
          Set RNameSaved = RName
          Nearest = RName.RefersToRange.Column
       End If
   Next
   NameChosen = RNameSaved.Name
   Call SelectItem(Replace(NameChosen, "chosen", "choice"), NameChosen)
 
End Sub



